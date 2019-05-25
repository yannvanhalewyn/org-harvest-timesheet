(ns hs.org
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [hs.utils :refer [file-exists? info read-json]]))

(defn ->json [filename]
  (when-not (file-exists? (io/file filename))
    (throw (ex-info (str "Could not find file: " filename)
                    {:filename filename})))
  (info (format "Transforming %s to json" filename))

  ;; When running in an uberjar, we need to copy the emacs-lisp file
  ;; out of the jar so that emacs can require it.
  (let [tmp (java.io.File/createTempFile "org2json" ".el")
        _ (spit tmp (slurp (io/resource "org2json.el")))
        result (sh "emacs" "--no-init-file" "-batch" "--quick"
                   "-l" (.getAbsolutePath tmp) "-f" "cli-org-export-json"
                   filename)]
    (io/delete-file tmp true)
    (if (zero? (:exit result))
      (read-json (:out result))
      (throw (ex-info (str "Could not parse org file. Make sure an up "
                           "to date version of emacs is installed and "
                           "on the classpath")
                      (assoc result :file filename))))))
