(ns hs.harvest
  (:require [clj-http.client :as http]
            [clj-time.core :as t :refer [weeks]]
            [clj-time.format :as f]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hs.file :as file]
            [hs.utils :refer [confirm! parse-int with-file-cache]]))

(defn- parse-date [d]
  (f/parse (f/formatter :date-time-no-ms) d))

(defn- timestamp [d]
  (f/unparse (f/formatter :date-time-no-ms) d))

(defn- date-readable [d]
  (f/unparse (f/formatter "MMM d, yyyy") d))

(defn- project-re [entry]
  (re-pattern (str/join ".*" (:entry/project-handles entry))))

(defn- project-search-name [p]
  (-> (str (:client/name p) (:project/name p))
      (str/replace #"\W" "")
      str/lower-case))

(defn- log [msg]
  (println "[Harvest]" msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsers

(defn- parse-projects [records]
  (for [{:keys [client] :as record} records]
    {:project/id (:id record)
     :project/code (:code record)
     :project/name (:name record)
     :project/created-at (parse-date (:created_at record))
     :project/updated-at (parse-date (:updated_at record))
     :client/id (:id client)
     :client/name (:name client)}))

(defn- parse-tasks [records]
  (for [{:keys [task]} records]
    {:task/id (:id task)
     :task/name (:name task)}))

(defn- parse-entries [records]
  (for [{:keys [project] :as r} records]
    {:entry/id (:id r)
     :entry/title (:notes r)
     :entry/spent-at (f/parse (f/formatter :date) (:spent_date r))
     :entry/hours (:hours r)
     :entry/locked? (:is_locked r)
     :project/name (:name project)
     :project/id (:id project)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client

(def ^:private BASE_URL "https://api.harvestapp.com/api/v2")

(defn- request [{::keys [access-token account-id]} {:keys [method path params query-params]}]
  (:body
   (http/request
    {:url (str BASE_URL path)
     :method (or method :get)
     :headers {"Harvest-account-id" account-id
               "Authorization" (str "Bearer " access-token)}
     :form-params params
     :query-params query-params
     :as :json})))

(defn- get-projects
  "Fetches the active projects from harvest. Caches the harvest
  response for one day."
  [{::keys [data-dir] :as client}]
  (parse-projects
   (:projects
    (with-file-cache {:ttl (weeks 1)
                      :file (io/file data-dir "cache/projects.edn")}
      (log "Fetching projects")
      (request client {:path "/projects.json"
                       :query-params {:is_active true}})))))

(defn- get-project-tasks
  [{::keys [data-dir] :as client} project-id]
  (parse-tasks
   (:task_assignments
    (let [cache-key (format "cache/project_%s_tasks.edn" project-id)
          path (str "/projects/" project-id "/task_assignments")]
      (with-file-cache {:ttl (weeks 1) :file (io/file data-dir cache-key)}
        (request client {:path path
                         :query-params {:is_active true}}))))))

(defn- get-entries [client {:keys [from to]}]
  (assert (t/before? from to))
  (log (format "Fetching existing entries between %s and %s"
               (date-readable from) (date-readable to)))
  (let [user (request client {:path "/users/me"})]
    (assert (:id user))
    (-> (request client {:path "/time_entries"
                         :query-params {:from (timestamp from)
                                        :user_id (:id user)
                                        :to (timestamp to)}})
        :time_entries parse-entries)))

(defn- find-project
  "Fetches the projects and attempts to find a match in the client
  name and task name. Will throw when none found, pick the most recent
  one if multiple are found."
  [projects re]
  (let [candidates (filter (comp (partial re-find re) project-search-name) projects)
        pick (last (sort-by :project/updated-at candidates))]
    (when (empty? candidates)
      (throw (ex-info "Could not find project" {:name re})))
    (when (> (count candidates) 1)
      (log (format "Multiple projects found for: '%s'. Picked: [%s] %s"
                   re (:client/name pick) (:project/name pick))))
    pick))

(defn- delete-existing-entries?
  "Checks for any existing entries in the time range. If any are
  found, will:
    - Print them out
    - Throw if any of them are locked
    - Ask to delete them otherwise, throw if cancelled by user."
  [client {:keys [from to] :as args}]
  (when-let [entries (seq (get-entries client args))]
    (log "Existing entries have been found for that time range:")
    (doseq [e entries]
      (println (format "  %s [%s] %s"
                       (date-readable (:entry/spent-at e))
                       (:project/name e) (:entry/title e))))
    (when (some :entry/locked? entries)
      (throw (ex-info "Locked entries detected in given time range."
                      {:type :harvest/cancelled})))
    (if (confirm! "Would you like to delete those entries?")
      (doseq [{:entry/keys [id]} entries]
        (request client {:path (str "/time_entries/" id) :method :delete}))
      (throw (ex-info "Won't delete conflicting entries"
                      {:type :harvest/cancelled})))))

(defn- post-time-entry*
  "Posts the time entry to Harvest. Will try to find a task for the
  given project. Throws if no task is found"
  [client project entry]
  (let [task (first (get-project-tasks client (:project/id project)))]
    (when-not (:task/id task)
      (throw (ex-info "Could not find default task for project"
                      {:project-id (:project/id project)
                       :entry entry})))
    (log (format "Pushing time-entry:\n  Entry: %s\n  Project: [%s] %s"
                 (:entry/_raw entry)
                 (:client/name project)
                 (:project/name project)))
    (request
     client
     {:path "/time_entries"
      :method :post
      :params {:project_id (:project/id project)
               :task_id (:task/id task)
               :spent_date (timestamp (:entry/spent-at entry))
               :hours (:entry/hours entry)
               :notes (:entry/title entry)}})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defn make-client
  "Reads the access-token and account-id from the env"
  [& [{:keys [access-token account-id]}]]
  (let [token (or access-token (System/getenv "HARVEST_ACCESS_TOKEN"))
        account-id (or account-id (parse-int (System/getenv "HARVEST_ACCOUNT_ID")))]
    (assert token "No harvest access token supplied")
    (assert account-id "No harvest account-id")
    {::access-token (or access-token (System/getenv "HARVEST_ACCESS_TOKEN"))
     ::account-id (or account-id (parse-int (System/getenv "HARVEST_ACCOUNT_ID")))
     ::data-dir (file/home ".harvest_sync")}))

(defn post-time-entries!
  "Will push all entries to Harvest. Will check if existing entries
  exist in that timerange. If so, will ask to delete those before
  pushing."
  [client entries]
  (when (empty? entries)
    (throw (ex-info "No entries to push." {})))
  (let [projects (get-projects client)
        with-projects (doall ;; Check for any errors before continuing
                       (for [e entries]
                         [e (find-project projects (project-re e))]))]

    ;; Check for existing entries
    (let [dates (map :entry/spent-at entries)
          from (apply t/min-date dates)
          to (apply t/max-date dates)]
      (delete-existing-entries? client {:from from :to to}))

    ;; Push entries
    (log (format "Syncing %s entries" (count entries)))
    (doseq [[entry project] with-projects]
      (post-time-entry* client project entry))))
