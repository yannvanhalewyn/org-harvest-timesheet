(ns hs.core
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn- keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))

(defn- parse-int [s]
  (when-let [x (re-find #"^-?\d+$" (str s))]
    (Integer. x)))

(defn- read-json [filename]
  (with-open [rdr (io/reader (io/file filename))]
    (json/read rdr :key-fn keywordize)))

(defn parse-org-node
  "Given the raw json from and org file, recursively parse it into a
  clojure tree"
  [[type props & children]]
  {:node/type (keywordize type)
   :node/value (:raw-value props)
   :node/tags (:tags props)
   :node/children (map parse-node children)})

(defn parse-week [data]
  (let [root (parse-org-node data)
        weeks (:node/children root)
        entries (mapcat :node/children (mapcat :node/children weeks))]
    (map :node/value entries)))

(comment
  (parse-week (read-json "timesheet.json"))
  )
