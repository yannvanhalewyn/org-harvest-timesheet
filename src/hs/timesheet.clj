(ns hs.timesheet
  (:require [clj-time.core :as t]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [hs.utils :refer [assert-spec! assert-spec+! keywordize parse-int]])
  (:import clojure.lang.PersistentVector))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Specs

(s/def :node/type keyword?)
(s/def :node/value (s/nilable string?))
(s/def :node/tags (s/nilable (s/coll-of string?)))
(s/def :node/children (s/coll-of :node/model))
(s/def :node/model
  (s/keys :req [:node/type :node/value :node/tags :node/children]))

(s/def :entry/hours pos?)
(s/def :entry/title string?)
(s/def :entry/project-handles (s/+ string?))
(s/def :entry/spent-at (partial instance? org.joda.time.DateTime))
(s/def :entry/model
  (s/keys :req [:entry/hours
                :entry/title
                :entry/project-handles
                :entry/spent-at]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing org timesheet

(def MONTHS ["jan" "feb" "mar" "apr" "may" "jun" "jul" "aug" "sep" "oct" "nov" "dec"])
(def WEEKDAYS ["monday" "tuesday" "wednesday" "thursday" "friday" "saturday" "sunday"])

(defn- index-of [^PersistentVector coll x]
  (let [i (.indexOf coll (str/lower-case x))]
    (when (= -1 i)
      (throw (ex-info "Couldn't find element in coll"
                      {:coll coll :element x})))
    i))

(defn- parse-org-node
  "Given the raw json from and org file, recursively parse it into a
  clojure tree"
  [[type props & body]]
  (let [[value children] (if (= "paragraph" type)
                           [(first body) nil]
                           [(:raw-value props) body])]
    {:node/type (keywordize type)
     :node/value value
     :node/tags (:tags props)
     :node/children (map parse-org-node children)}))

(defn parse-weekday
  "Figures which date it should be in a given week using the
  conventions of our timesheet.
   Ex: \"20 may\" \"tuesday\" => #inst \"2019-05-21\" "
  [week-str weekday-str]
  (let [[d m] (str/split week-str #" ")
        year (t/year (t/now))
        month (inc (index-of MONTHS m))
        day (parse-int d)
        monday (t/date-time year month day)]
    (assert (= 1 (t/day-of-week monday))
            (str week-str " is not a monday!"))
    (t/plus monday (t/days (index-of WEEKDAYS weekday-str)))))

(defn parse-entry-title
  "Parses the raw text of an entry value like:
     11:00 - 11:30 Something => [0.5 \"Something\"]"
  [s]
  (if-let [[_ h1 m1 h2 m2 text]
           (re-find #"^(\d{1,2})(?::(\d{2}))? - (\d{1,2})(?::(\d{2}))?(.*)" s)]
    [(double (- (+ (parse-int h2) (if m2 (/ (parse-int m2) 60) 0))
                (+ (parse-int h1) (if m1 (/ (parse-int m1) 60) 0))))
     (str/trim text)]
    (throw (ex-info "Could not parse time entry" {:value s}))))

(defn- filter-weeks [week week-nodes]
  (case week
    (:all nil) week-nodes
    :last (take-last 1 week-nodes)
    (filter #(= (str/lower-case week) (str/lower-case (:node/value %)))
            week-nodes)))

(defn- time-entries
  "Given a week node, parse the entries duration, title and timestamp"
  [default-project week]
  (flatten
   (for [day (:node/children week)]
     (for [entry (filter (comp #{:headline} :node/type) (:node/children day))
           :let [time (parse-weekday (:node/value week) (:node/value day))
                 [hours title] (parse-entry-title (:node/value entry))]]
       {:entry/hours hours
        :entry/title title
        :entry/spent-at time
        :entry/project-handles
        (when-let [p (or (first (:node/tags entry)) default-project)]
          (str/split p #"%"))}))))

(defn parse
  "Given a json datastructure which comes from serializing the org
  file, parse and return a list of entries ready to be pushed to the
  time tracker."
  [data {:keys [default-project week]}]
  (->> (parse-org-node data)
       (:node/children)
       (filter-weeks week)
       (assert-spec+! :node/model)
       (mapcat (partial time-entries default-project))
       (assert-spec+! :entry/model)))

(comment
  (parse (hs.org/->json "/Users/yannvanhalewyn/Google Drive/Documents/timesheet.org"))

  )
