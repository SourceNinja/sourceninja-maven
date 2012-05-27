(ns sourceninja.maven.plugin

  (:use clojure.maven.mojo.defmojo
        clojure.maven.mojo.log)

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

    (println "ID" id)
    (println "TOKEN" token)
    (println "URL" url)

    (let [root (.buildDependencyTree tree-builder
                                     project
                                     local-repo
                                     artifact-fact
                                     artifact-meta
                                     nil
                                     artifact-colc)

          direct (into #{} (map node-to-hash (.getChildren root)))
          indirect (set/difference (into #{} (flatten-deps (.getChildren root))) direct)
          tmp (doto (java.io.File/createTempFile "pre" ".suff") .deleteOnExit)
          deps (json/generate-string (concat
                                      (map #(set-direct %1 true) direct)
                                      (map #(set-direct %1 false) indirect)))]

      (with-open [w (clojure.java.io/writer tmp)]
        (.write w deps))

      (http/post
       (sn-post-url url "foo")
       {:multipart {"token" "bar"
                    "meta_source_type" "maven"
                    "import_type" "json"
                    "import[import]" tmp}})


      ))

  (setLog [_ logger] (set! log logger))
  (getLog [_] log)

  ContextEnabled
  (setPluginContext [_ context] (reset! plugin-context context))
  (getPluginContext [_] @plugin-context))

(defn make-SourceNinjaMojo
  "Function to provide a no argument constructor"
  []
  (SourceNinjaMojo. nil nil nil nil nil nil nil nil nil nil nil (atom nil)))
