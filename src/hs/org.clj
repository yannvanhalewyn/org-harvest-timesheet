(ns hs.org
  (:require [hs.utils :refer [read-json]]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]))

(defn ->json [filename]
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
