(ns com.sourceninja.maven.plugin

  (:use clojure.maven.mojo.defmojo
        clojure.maven.mojo.log
        [slingshot.slingshot :only [throw+ try+]])

  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.plexus.factory.component-factory :as plexus]
            [clojure.set :as set])

  (:import
   [clojure.maven.annotations Goal RequiresDependencyResolution Parameter Component]
   [org.apache.maven.plugin ContextEnabled Mojo MojoExecutionException]
   org.apache.maven.artifact.factory.ArtifactFactory
   org.apache.maven.artifact.metadata.ArtifactMetadataSource
   org.apache.maven.artifact.resolver.ArtifactCollector
   org.apache.maven.shared.dependency.tree.DependencyNode
   org.apache.maven.shared.dependency.tree.DependencyTreeBuilder))

(defn trace-element-to-string
  [e]
  (str
   (let [class (.getClassName e)
         method (.getMethodName e)]
     (let [match (re-matches #"^([A-Za-z0-9_.-]+)\$(\w+)__\d+$" class)]
       (if (and match (= "invoke" method))
         (apply format "%s/%s" (rest match))
         (format "%s.%s" class method))))
   (format " (%s:%d)" (or (.getFileName e) "") (.getLineNumber e))))

(defn stack-trace-to-string
  ([tr] (stack-trace-to-string tr nil))
  ([tr n]
     (let [st (.getStackTrace tr)]
       (clojure.string/join
        (for [e (if (nil? n)
                  (rest st)
                  (take (dec n) (rest st)))]
          (format "    %s\n" (trace-element-to-string e)))))))

(defn exception-to-string
  [tr]
  (format "%s: %s" (.getName (class tr)) (.getMessage tr)))

(defn sn-post-url
  [host id]
  (str host "/products/" id "/imports"))

(defn artifact-to-hash
  [a]
  {"name" (str (.getGroupId a) ":" (.getArtifactId a))
   "version" (.getVersion a)})

(defn node-to-hash
  [n]
  (artifact-to-hash
   (.getArtifact n)))

(defn set-direct
  [h v]
  (assoc h "direct" v))

(defn flatten-deps
  [input]
  (loop [output []
         input (flatten (map #(into [] (.getChildren %1)) input))]
    (if (empty? input)
      output
      (recur
       (concat output (map node-to-hash (filter #(= (.getState %1) DependencyNode/INCLUDED) input)))
       (flatten (map #(into [] (.getChildren %1)) input))))))

(defn http-response-map?
  [hrm]
  (get hrm :status false))

(deftype
    ^{Goal "send"
      RequiresDependencyResolution "test"}
    SourceNinjaMojo
  [
   ^{Parameter
     {:expression "${basedir}" :required true :readonly true}}
   base-dir

   ^{Parameter
     {:expression "${project}" :required true :readonly true}}
   project

   ^{Parameter
     {:expression "${localRepository}" :required true :readonly true}}
   local-repo

   ^{Parameter
     {:expression "${send.id}" :required true}}
   ^String id

   ^{Parameter
     {:expression "${send.token}" :required true :typename "java.lang.String"}}
   ^String token

   ^{Parameter
     {:expression "${send.url}" :required true :defaultValue "https://app.sourceninja.com"}}
   ^String url

   ^{Component
     {:role "org.apache.maven.artifact.factory.ArtifactFactory" :required true}}
   artifact-fact

   ^{Component
     {:role "org.apache.maven.artifact.resolver.ArtifactCollector" :required true}}
   artifact-colc

   ^{Component
     {:role "org.apache.maven.artifact.metadata.ArtifactMetadataSource" :required true}}
   artifact-meta

   ^{Component
     {:role "org.apache.maven.shared.dependency.tree.DependencyTreeBuilder" :required true}}
   tree-builder

   ^{:volatile-mutable true}
   log

   plugin-context]

  Mojo
  (execute [this]

    (cond
     (nil? tree-builder) (.error log "Maven did not instantiate a valid tree builder")
     (nil? project) (.error log "Maven did not instantiate a valid project")
     (nil? local-repo) (.error log "Maven did not instantiate a valid local repo")
     (nil? artifact-fact) (.error log "Maven did not instantiate a valid artifact factory")
     (nil? artifact-meta) (.error log "Maven did not instantiate a valid artifact metadata source")
     (nil? artifact-colc) (.error log "Maven did not instantiate a valid artifact collector")
     :else (try
             (let [root (.buildDependencyTree tree-builder
                                              project
                                              local-repo
                                              artifact-fact
                                              artifact-meta
                                              nil
                                              artifact-colc)

                   direct (into #{} (map node-to-hash (.getChildren root)))
                   indirect (set/difference (into #{} (flatten-deps (.getChildren root))) direct)
                   tmp (doto (java.io.File/createTempFile "maven" ".json") .deleteOnExit)
                   deps (json/generate-string (concat
                                               (map #(set-direct %1 true) direct)
                                               (map #(set-direct %1 false) indirect)))]

               (.debug log deps)
               (with-open [w (clojure.java.io/writer tmp)]
                 (.write w deps))

               (try+
                 (http/post
                  (sn-post-url url id)
                  {:multipart {"token" token
                               "meta_source_type" "maven"
                               "import_type" "json"
                               "import[import]" tmp}
                   :throw-entire-message? true})

                 (catch http-response-map? {:keys [status]}
                   (cond
                    (= status 404) (throw (org.apache.maven.plugin.MojoFailureException.
                                           (format "Invalid SourceNinja product ID" status)))

                    (= status 403) (throw (org.apache.maven.plugin.MojoFailureException.
                                           (format "Invalid SourceNinja product token" status)))

                    :else (throw+))))

               (.info log "Successfully uploaded data to SourceNinja"))

             (catch Exception e
               (.error log (str (exception-to-string e) (stack-trace-to-string e)))))))

     (setLog [_ logger] (set! log logger))
     (getLog [_] log)

  ContextEnabled
  (setPluginContext [_ context] (reset! plugin-context context))
  (getPluginContext [_] @plugin-context))

(defn make-SourceNinjaMojo
  "Function to provide a no argument constructor"
  []
  (SourceNinjaMojo. nil nil nil nil nil nil nil nil nil nil nil (atom nil)))
