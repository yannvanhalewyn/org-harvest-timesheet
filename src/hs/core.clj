(ns hs.core
  (:gen-class)
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [hs.harvest :as harvest]
            [hs.org :as org]
            [hs.sync :as sync]
            [hs.timesheet :as timesheet]
            [hs.utils :refer [error info stacktrace-str]]))

(def cli-options
  [["-p" "--default-project PROJECT"
    "Default project. Same as org tags, use % as a wildcard"]
   ["-w" "--week WEEK"
    "The week. One of 'all', 'last' or a weekstring like '20 May'"
    :default :all :default-desc "all"
    :parse-fn #(case % ("all" "last") (keyword %) %)]
   ["-t" "--harvest-access-token TOKEN"
    "The Harvest access token, defaults to HARVEST_ACCESS_TOKEN env"]
   ["-a" "--harvest-account-id ACCOUNT_ID"
    "The Harvest access token, defaults to HARVEST_ACCOUNT_ID env"]
   ["-h" "--help" "Show this message"]])

(defn- usage-summary [summary]
  (str "Usage: harvest sync FILENAME <options>\n\n" summary))

(defn- parse-args [args]
  (let [{:keys [options summary arguments errors]} (cli/parse-opts args cli-options)
        exit-with-summary (fn [& [err]]
                            {:exit-msg (str err (when err "\n\n") (usage-summary summary))
                             :ok? (not err)})]
    (cond
      (:help options)    (exit-with-summary)
      (empty? arguments) (exit-with-summary)
      (seq errors)       (exit-with-summary (str/join " " errors))
      (= "sync" (first arguments))
      (if (= 2 (count arguments))
        {:action :sync :opts (assoc options :filename (second arguments))}
        (exit-with-summary "No filename provided"))
      :else (exit-with-summary (str "No such action: " (first arguments))))))

(defn- exit! [msg ok?]
  (let [[log exit-code] (if ok? [info 0] [error -1])]
    (log msg)
    (System/exit exit-code)))

(defn- explain [^Throwable e]
  (str (or (.getMessage e) (stacktrace-str e)) "\n"
       (when-let [{:keys [type] :as d} (ex-data e)]
         (if (= :spec/error type)
           (:explain d)
           (with-out-str (pprint d))))))

(defn- sync!
  "Reads the org file, extracts the time entries and pushes them to
  harvest"
  [{:keys [filename] :as options}]
  (sync/sync!
   (harvest/make-client options)
   (timesheet/parse (org/->json filename) options)))

(defn -main [& args]
  (let [{:keys [exit-msg ok? opts]} (parse-args args)]
    (if exit-msg
      (exit! exit-msg ok?)
      (try (sync! opts)
           (catch Exception e (exit! (explain e) false)))))
  (exit! "Bye" true))
