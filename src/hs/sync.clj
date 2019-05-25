(ns hs.sync
  (:require [clj-time.core :as t]
            [clojure.string :as str]
            [hs.harvest :as harvest]
            [hs.utils :as u]))

(defn- log [msg]
  (u/info (u/colorize :yellow "Sync") msg))

(defn- format-project-name [entry]
  (u/colorize :cyan (format "[%s %s]" (:client/name entry)
                            (:project/name entry))))

(defn- format-entry [entry & [prefix-color prefix]]
  (format "    %s %s %s %s"
          (if prefix (u/colorize prefix-color prefix) "")
          (u/colorize :grey (u/readable-date (:entry/spent-at entry)))
          (format-project-name entry)
          (:entry/title entry)))

(defn- log-entries [entries & [prefix-color prefix]]
  (u/info "")
  (doseq [entry entries] (u/info (format-entry entry prefix-color prefix)))
  (u/info ""))

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
      (log (format "Multiple projects found for: '%s'. Picked: %s"
                   re (format-project-name pick))))
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

    (when-let [locked-entries (seq (filter :entry/locked? existing-entries))]
      (log-entries locked-entries :red "LOCKED")
      (throw (ex-info "Locked entries detected in given time range."
                      {:type :sync/cancelled})))

    (when (seq to-delete)
      (u/info (str "\nThese entries will be " (u/colorize :red "DELETED")))
      (log-entries to-delete))

    (when (seq to-push)
      (u/info (str "\nThese entries will be " (u/colorize :green "PUSHED")))
      (log-entries to-push))

    (if (every? empty? [to-push to-delete])
      (log "Nothing to be done.")
      (do (when-not (u/confirm! "\nApply?")
            (throw (ex-info "User cancelled operation"
                            {:type :sync/cancelled})))

          (doseq [{:entry/keys [id] :as entry} to-delete]
            (u/info (format-entry entry :red "DELETE"))
            (harvest/delete-entry! client id))

          (doseq [entry to-push]
            (u/info (format-entry entry :green "PUSH  "))
            (harvest/create-entry! client entry))))))
