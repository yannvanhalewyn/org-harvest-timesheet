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

(defn parse-entry-value
  "Parses the raw text of an entry value like:
     11:00 - 11:30 Something => [0.5 \"Something\"]"
  [s]
  (if-let [[_ h1 m1 h2 m2 text]
           (re-find #"(\d{2})(?::(\d{2}))? - (\d{2})(?::(\d{2}))?(.*)" s)]
    [(double (- (+ (parse-int h2) (if m2 (/ (parse-int m2) 60) 0))
                (+ (parse-int h1) (if m1 (/ (parse-int m1) 60) 0))))
     (str/trim text)]
    (throw (ex-info "Could not parse time entry" {:value s}))))

(defn parse-entry [{:node/keys [value tags]}]
  (let [[duration name] (parse-entry-value value)]
    {:entry/duration duration
     :entry/name name
     :entry/project (first tags)
     :entry/_raw value}))

(defn parse-week [data]
  (let [root (parse-org-node data)
        weeks (:node/children root)
        entries (mapcat :node/children (mapcat :node/children weeks))]
    (map parse-entry entries)))

(comment
  (parse-week (read-json "timesheet.json"))
  )
