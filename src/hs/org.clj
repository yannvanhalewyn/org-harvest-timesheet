(ns hs.org
  (:require [hs.utils :refer [read-json]]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [hs.file :as file]
            [hs.log :as log]))

(defn ->json [filename]
  (when-not (file/exists? (io/file filename))
    (throw (ex-info (str "Could not find file: " filename)
                    {:filename filename})))
  (log/info (format "Transforming %s to json" filename))
  (let [el-lib (io/file (io/resource "org2json.el"))
        result (sh "emacs" "--no-site-file" "--no-init-file" "-batch"
                   "-l" (.getAbsolutePath el-lib) "-f"
                   "cli-org-export-json" filename)]
    (if (zero? (:exit result))
      (read-json (:out result))
      (throw (ex-info "Could not parse org file"
                      {:file filename
                       :status (:exit result)
                       :output (:out result)})))))
