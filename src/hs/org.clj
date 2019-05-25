(ns hs.org
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [hs.utils :refer [file-exists? info read-json]]))

(defn ->json [filename]
  (when-not (file-exists? (io/file filename))
    (throw (ex-info (str "Could not find file: " filename)
                    {:filename filename})))
  (info (format "Transforming %s to json" filename))
  (let [el-lib (io/file (io/resource "org2json.el"))
        result (sh "emacs" "--no-site-file" "--no-init-file" "-batch"
                   "-l" (.getAbsolutePath el-lib) "-f"
                   "cli-org-export-json" filename)]
    (if (zero? (:exit result))
      (read-json (:out result))
      (throw (ex-info (str "Could not parse org file. Make sure an up "
                           "to date version of emacs is installed and "
                           "on the classpath")
                      (assoc result :file filename))))))
