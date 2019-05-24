(ns hs.core
  (:require [hs.harvest :as harvest]
            [hs.org :as org]
            [hs.timesheet :as timesheet]
            [clojure.string :as str]))

(defn sync!
  "Reads the org file, extracts the time entries and pushes them to
  harvest"
  [org-filename]
  (harvest/post-time-entries!
   (harvest/make-client)
   (timesheet/parse (org/->json org-filename))))
