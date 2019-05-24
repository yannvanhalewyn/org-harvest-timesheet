(ns hs.core
  (:require [hs.harvest :as harvest]
            [hs.org :as org]
            [hs.timesheet :as timesheet]
            [clojure.string :as str]))

(defn- find-project [projects entry]
  (let [re (re-pattern (str/join ".*" (:entry/project-handles entry)))]
    [entry (harvest/find-project projects re)]))

(defn sync!
  "Reads the org file, extracts the time entries and pushes them to
  harvest"
  [org-filename]
  (let [client (harvest/make-client)
        entries (map (partial find-project (harvest/get-projects client))
                     (timesheet/parse (org/->json org-filename)))]
    (doseq [[entry project] (doall entries)] ;; Throw any exceptions before pushing
      (harvest/post-time-entry! client project entry))))
