(ns hs.core
  (:gen-class)
  (:require [hs.sync :as sync]
            [hs.harvest :as harvest]
            [hs.org :as org]
            [hs.timesheet :as timesheet]
            [hs.utils :refer [error]]))

(defn- sync!
  "Reads the org file, extracts the time entries and pushes them to
  harvest"
  [org-filename]
  (sync/sync!
   (harvest/make-client)
   (timesheet/parse (org/->json org-filename))))

(defn -main [& [cmd arg1]]
  (try
    (case cmd
      "sync" (sync! arg1)
      (println "Usage:   harvest sync [filename]"))
    (catch Exception e
      (error (.getMessage e) (ex-data e))
      (System/exit -1)))
  (System/exit 0))
