(ns hs.core
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [hs.harvest :as harvest]
            [hs.org :as org]
            [hs.sync :as sync]
            [hs.timesheet :as timesheet]
            [hs.utils :refer [error]]))

(def cli-options
  [["-p" "--default-project PROJECT"
    "Default project. Same as org tags, use % as a wildcard"]
   ["-w" "--week WEEK"
    "The week. One of 'all', 'last' or a weekstring like '20 May'"
    :default :all
    :parse-fn #(case % ("all" "last") (keyword %) %)]
   ["-t" "--harvest-access-token TOKEN"
    "The Harvest access token, defaults to HARVEST_ACCESS_TOKEN env"]
   ["-a" "--harvest-account-id ACCOUNT_ID"
    "The Harvest access token, defaults to HARVEST_ACCOUNT_ID env"]
   ["-h" "--help" "Show this message"]])

(defn- sync!
  "Reads the org file, extracts the time entries and pushes them to
  harvest"
  [org-filename options]
  (sync/sync!
   (harvest/make-client options)
   (timesheet/parse (org/->json org-filename) options)))

(defn- print-usage [summary]
  (println "Usage: harvest sync FILENAME <options>\n\n" (subs summary 1)))

(defn -main [& args]
  (let [{:keys [options summary arguments errors]}
        (cli/parse-opts args cli-options)]
    (when (seq errors)
      (error (str/join " " errors))
      (print-usage summary)
      (System/exit -1))
    (if (:help options)
      (print-usage summary)
      (try
        (case (first arguments)
          "sync" (sync! (second arguments) options)
          (print-usage summary))
        (catch Exception e
          (if-let [msg (.getMessage e)]
            (error (.getMessage e) (ex-data e))
            (.printStackTrace e))
          (System/exit -1))))
    (System/exit 0)))
