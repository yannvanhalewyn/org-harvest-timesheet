(ns hs.timesheet
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [hs.utils :refer [keywordize parse-int assert-spec! assert-spec+!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Specs

(s/def :node/type keyword?)
(s/def :node/value (s/nilable string?))
(s/def :node/tags (s/nilable (s/coll-of string?)))
(s/def :node/children (s/coll-of :node/model))
(s/def :node/model
  (s/keys :req [:node/type :node/value :node/tags :node/children]))

(s/def :entry/hours pos?)
(s/def :entry/name string?)
(s/def :entry/project-handle (s/nilable string?))
(s/def :entry/model
  (s/keys :req [:entry/hours :entry/name :entry/project-handle]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing org timesheet

(defn- parse-org-node
  "Given the raw json from and org file, recursively parse it into a
  clojure tree"
  [[type props & children]]
  {:node/type (keywordize type)
   :node/value (:raw-value props)
   :node/tags (:tags props)
   :node/children (map parse-org-node children)})

(defn- parse-entry-value
  "Parses the raw text of an entry value like:
     11:00 - 11:30 Something => [0.5 \"Something\"]"
  [s]
  (if-let [[_ h1 m1 h2 m2 text]
           (re-find #"(\d{2})(?::(\d{2}))? - (\d{2})(?::(\d{2}))?(.*)" s)]
    [(double (- (+ (parse-int h2) (if m2 (/ (parse-int m2) 60) 0))
                (+ (parse-int h1) (if m1 (/ (parse-int m1) 60) 0))))
     (str/trim text)]
    (throw (ex-info "Could not parse time entry" {:value s}))))

(defn- parse-entry [{:node/keys [value tags]}]
  (let [[hours name] (parse-entry-value value)]
    {:entry/hours hours
     :entry/name name
     :entry/project-handle (first tags)
     :entry/_raw value}))

(defn parse-week [data]
  (let [root (assert-spec! :node/model (parse-org-node data))
        weeks (:node/children root)
        entries (mapcat :node/children (mapcat :node/children weeks))]
    (assert-spec+! :entry/model (map parse-entry entries))))

(comment
  (parse-week (hs.utils/read-json "timesheet.json"))

  )
