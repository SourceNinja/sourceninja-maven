(ns sourceninja.maven.plugin
  (:use clojure.maven.mojo.defmojo
        clojure.maven.mojo.log)
  (:require [clojure.plexus.factory.component-factory :as plexus]
            [clojure.set :as set])
  (:import
   org.apache.maven.artifact.resolver.ArtifactCollector
   org.apache.maven.artifact.factory.ArtifactFactory
   org.apache.maven.artifact.metadata.ArtifactMetadataSource
   org.apache.maven.shared.dependency.tree.DependencyTreeBuilder
   [clojure.maven.annotations Goal RequiresDependencyResolution Parameter Component]
   org.apache.maven.plugin.ContextEnabled
   org.apache.maven.plugin.Mojo
   org.apache.maven.plugin.MojoExecutionException))

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
      (distinct output)
      (recur
       (concat output (map node-to-hash input))
       (flatten (map #(into [] (.getChildren %1)) input))))))

(deftype
    ^{Goal "send"
      RequiresDependencyResolution "test"}
    SourceNinjaMojo
  [
   ^{Parameter
     {:expression "${basedir}" :required true :readonly true}}
   base_dir

   ^{Parameter
     {:expression "${project}" :required true :readonly true}}
   project

   ^{Parameter
     {:expression "${localRepository}" :required true :readonly true}}
   local_repo

   ^{Parameter
     {:expression "${sourceninja.maven.plugin.product-id}" :required true :readonly true}}
   product_id

   ^{Parameter
     {:expression "${sourceninja.maven.plugin.product-token}" :required true :readonly true}}
   product_token

   ^{Parameter
     {:expression "${sourceninja.maven.plugin.url}" :required true :readonly true :defaultValue "https://app.sourceninja.com"}}
   url

   ^{Component
     {:role "org.apache.maven.artifact.factory.ArtifactFactory" :required true}}
   artifact_fact

   ^{Component
     {:role "org.apache.maven.artifact.resolver.ArtifactCollector" :required true}}
   artifact_colc

   ^{Component
     {:role "org.apache.maven.artifact.metadata.ArtifactMetadataSource" :required true}}
   artifact_meta

   ^{Component
     {:role "org.apache.maven.shared.dependency.tree.DependencyTreeBuilder" :required true}}
   tree_builder

   ^{:volatile-mutable true}
   log

   plugin-context]

  Mojo
  (execute [this]

    (let [root (.buildDependencyTree tree_builder
                                     project
                                     local_repo
                                     artifact_fact
                                     artifact_meta
                                     nil
                                     artifact_colc)

          direct (into #{} (map node-to-hash (.getChildren root)))
          indirect (set/difference (into #{} (flatten-deps (.getChildren root))) direct)]

      (println
       (concat
        (map #(set-direct %1 true) direct)
        (map #(set-direct %1 false) indirect))))






    ;;   (println node))
    )

  (setLog [_ logger] (set! log logger))
  (getLog [_] log)

  ContextEnabled
  (setPluginContext [_ context] (reset! plugin-context context))
  (getPluginContext [_] @plugin-context))

(defn make-SourceNinjaMojo
  "Function to provide a no argument constructor"
  []
  (SourceNinjaMojo. nil nil nil nil nil nil nil nil nil nil nil (atom nil)))





    ;;(println (map artifact-to-hash (.getArtifacts project)))))







;;     (let [result (.artifactCollector
;;                   this
;;                   (.getArtifacts project)
;;                   (.getArtifact project)
;;                   (.getLocal this)
;;                   (.remoteRepos this)
;;                   (.artifactMetadataSource this)
;;                   (ScopeArtifactFilter. Artifact/SCOPE_TEST)
;;                   (vector))]

;;       (doseq [n (.getArtifactResolutionNodes result)]
;;         (println (.getRemoteRepositories n))))))

;; (comment
;;   (compile 'sourceninja.maven-plugin)
;;   (let [c (sourceninja.maven-plugin.)]
;;     (.execute c))
;;   )
