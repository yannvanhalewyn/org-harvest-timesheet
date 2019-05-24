(ns hs.utils
  (:require [clj-time.core :as t]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hs.file :as file]
            [clojure.spec.alpha :as s]))

(defn keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))

(defn parse-int [s]
  (when-let [x (re-find #"^-?\d+$" (str s))]
    (Integer. x)))

(defn read-json [filename]
  (with-open [rdr (io/reader (io/file filename))]
    (json/read rdr :key-fn keywordize)))

(defn assert-spec! [spec x]
  (when-not (s/valid? spec x)
    (throw (ex-info "Spec failed!"
                    {:spec spec
                     :failure (s/explain-str spec x)
                     :value x})))
  x)

(defn assert-spec+! [spec coll]
  (doseq [x coll] (assert-spec! spec x))
  coll)

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
