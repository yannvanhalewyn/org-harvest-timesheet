(ns hs.core
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [hs.harvest :as harvest]
            [hs.org :as org]
            [hs.sync :as sync]
            [hs.timesheet :as timesheet]
            [hs.utils :refer [error]]))

;; harvest sync file.org --default-project foo-bar --week all|last|"20 May"

(def cli-options
  [["-p" "--default-project PROJECT" "Default project"
    :parse-fn #(when % (str/split % #"-"))]
   ["-w" "--week WEEK"
    "The week. One of 'all', 'last' or a weekstring like '20 May'"
    :default :all
    :parse-fn #(case % ("all" "last") (keyword %) %)]
   ["-h" "--help" "Show this message"]])

(defn- sync!
  "Reads the org file, extracts the time entries and pushes them to
  harvest"
  [org-filename options]
  (sync/sync!
   (harvest/make-client)
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
          (error (.getMessage e) (ex-data e))
          (System/exit -1))))
    (System/exit 0)))
