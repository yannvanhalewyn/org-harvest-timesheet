(ns hs.core
  (:gen-class)
  (:require [hs.sync :as sync]
            [hs.harvest :as harvest]
            [hs.org :as org]
            [hs.timesheet :as timesheet]
            [hs.utils :refer [error]]
            [clojure.string :as str]))

(defn- sync!
  "Reads the org file, extracts the time entries and pushes them to
  harvest"
  [org-filename default-project]
  (sync/sync!
   (harvest/make-client)
   (timesheet/parse (org/->json org-filename) default-project)))

(defn -main [& [cmd arg1 _ arg3]]
  (try
    (case cmd
      "sync" (sync! arg1 (when arg3 (str/split arg3 #"-")))
      (println "Usage:   harvest sync [filename] --default-project client-project"))
    (catch Exception e
      (error (.getMessage e) (ex-data e))
      (System/exit -1)))
  (System/exit 0))
