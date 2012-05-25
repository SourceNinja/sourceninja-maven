(ns sourceninja.maven.plugin
  (:use clojure.maven.mojo.defmojo
        clojure.maven.mojo.log)
  (:import
   ;; org.apache.maven.artifact.Artifact
   ;; org.apache.maven.shared.artifact.filter.ScopeArtifactFilter
   ;; org.apache.maven.plugin.ContextEnabled
   ;; org.apache.maven.plugin.Mojo
   ;; org.apache.maven.plugin.MojoExecutionException
   ))

(defn sn-post-url
  [host id]
  (str host "/products/" id "/imports"))

(defmojo SourceNinjaMojo
  {:goal "send"
   :requires-dependency-resolution "test"}

  [base-dir      {:expression "${basedir}" :required true :readonly true}
   project       {:expression "${project}" :required true :readonly true}
   product_id    {:expression "${sourceninja.maven.plugin.product-id}" :required true}
   product_token {:expression "${sourceninja.maven.plugin.product-token}" :required true}
   url           {:expression "${sourceninja.maven.plugin.url}" :defaultValue "https://app.sourceninja.com"}]

  (println (class project)))


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
