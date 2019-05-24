(ns hs.utils
  (:require [clj-time.core :as t]
            [clojure.java.io :as io]
            [hs.file :as file]))

(defn with-file-cache* [{:keys [ttl file]} f]
  (if (or (not (file/exists? file))
          (t/after? (t/now) (t/plus (file/last-modified file) ttl)))
    (let [data (f)]
      (io/make-parents file)
      (spit (.getAbsolutePath file) (pr-str data))
      data)
    (read-string (slurp file))))

(defmacro with-file-cache [opts & body]
  `(with-file-cache* ~opts (fn [] ~@body)))
