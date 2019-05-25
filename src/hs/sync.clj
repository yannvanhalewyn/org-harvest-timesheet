(ns hs.sync
  (:require [clj-time.core :as t]
            [clojure.string :as str]
            [hs.harvest :as harvest]
            [hs.utils :as u]))

(defn- log [msg]
  (u/info (u/colorize :yellow "Sync") msg))

(defn- log-entry [entry]
  (format "%s %s %s"
          (u/colorize :grey (u/readable-date (:entry/spent-at entry)))
          (u/colorize :cyan (format "[%s %s]" (:client/name entry)
                                    (:project/name entry)))
          (:entry/title entry)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Finding project for entry

(defn- project-search-name [p]
  (-> (str (:client/name p) (:project/name p))
      (str/replace #"\W" "")
      str/lower-case))

(defn- find-project
  "Given a list of projects, attempts to find a match in the project's
  name and task name. Will throw when none found, pick the most recent
  one if multiple are found."
  [projects entry]
  (let [re (re-pattern (str/join ".*" (:entry/project-handles entry)))
        candidates (filter (comp (partial re-find re) project-search-name) projects)
        pick (last (sort-by :project/updated-at candidates))]
    (when (empty? candidates)
      (throw (ex-info "Could not find project" {:name re})))
    (when (> (count candidates) 1)
      (log (format "Multiple projects found for: '%s'. Picked: [%s] %s"
                   re (:client/name pick) (:project/name pick))))
    pick))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Comparing local <> remote entries

(def ^:private comparable
  (juxt :entry/title :project/id :client/id :entry/spent-at
        :entry/hours))

(defn- compare-entries
  "Will compare a local set and remote set of entries. Will return the
  ones missing remotely, and the ones that are present remotely but
  not locally"
  [local-entries remote-entries]
  (let [local? (set (map comparable local-entries))
        remote? (set (map comparable remote-entries))]
    {:to-push (remove (comp remote? comparable) local-entries)
     :to-delete (remove (comp local? comparable) remote-entries)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defn sync!
  "Will fetch all existing entries for the time range of the local
  entries, compare them, print out a diff for the user and then apply
  the sync (delete missing locally, pushing missing remotely) "
  [client entries]
  (when (empty? entries)
    (throw (ex-info "No entries to push." {})))
  (let [projects (harvest/get-projects client)
        entries (for [entry entries
                      :let [project (find-project projects entry)
                            task (first (harvest/get-project-tasks
                                         client (:project/id project)))]]
                  (merge entry project task))
        dates (map :entry/spent-at entries)
        existing-entries (harvest/get-entries client {:from (apply t/min-date dates)
                                                      :to (apply t/max-date dates)})
        {:keys [to-push to-delete]} (compare-entries entries existing-entries)]

    (when-let [entry (first (remove :task/id to-push))]
      (throw (ex-info "Could not find default task for project" {:entry entry})))

    (when (some :entry/locked? existing-entries)
      (throw (ex-info "Locked entries detected in given time range."
                      {:type :harvest/cancelled})))

    (when (seq to-delete)
      (u/info (str "\nThese entries will be " (u/colorize :red "DELETED")))
      (doseq [e to-delete] (u/info (str "\t" (log-entry e)))))

    (when (seq to-push)
      (u/info (str "\nThese entries will be " (u/colorize :green "ADDED")))
      (doseq [e to-push] (u/info (str "\t" (log-entry e)))))

    (if (every? empty? [to-push to-delete])
      (log "Nothing to be done.")
      (do (when-not (u/confirm! "\nApply?")
            (throw (ex-info "User cancelled operation"
                            {:type :harvest/cancelled})))

          (doseq [{:entry/keys [id] :as entry} to-delete]
            (u/info (str (u/colorize :red "\tDELETE ") (log-entry entry)))
            (harvest/delete-entry! client id))

          (doseq [entry to-push]
            (u/info (str (u/colorize :green "\tPUSH   ") (log-entry entry)))
            (harvest/create-entry! client entry))))))
