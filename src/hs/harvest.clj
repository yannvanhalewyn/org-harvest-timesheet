(ns hs.harvest
  (:require [clj-http.client :as http]
            [clj-time.core :refer [weeks]]
            [clj-time.format :as f]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hs.file :as file]
            [hs.utils :refer [with-file-cache]]
            [clj-time.core :as t]))

(defn- parse-date [d]
  (f/parse (f/formatter :date-time-no-ms) d))

(defn- timestamp [d]
  (f/unparse (f/formatter :date-time-no-ms) d))

(defn- project-search-name [p]
  (-> (str (:client/name p) (:project/name p))
      (str/replace #"\W" "")
      str/lower-case))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client

(def ^:private BASE_URL "https://api.harvestapp.com/api/v2")

(defn- request [{::keys [access-token account-id]} {:keys [method path params query-params]}]
  (http/request
   {:url (str BASE_URL path)
    :method (or method :get)
    :headers {"Harvest-account-id" account-id
              "Authorization" (str "Bearer " access-token)}
    :form-params params
    :query-params query-params
    :as :json}))

(defn- get-projects* [client]
  (println "Fetching harvest projects")
  (request client {:path "/projects.json"
                   :query-params {:is_active true}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defn make-client
  "Reads the access-token and account-id from the env"
  [& [{:keys [access-token account-id]}]]
  {::access-token (or access-token (System/getenv "HARVEST_API_TOKEN"))
   ::account-id (or account-id (System/getenv "HARVEST_ACCOUNT_ID"))
   ::data-dir (file/home ".harvest_sync")})

(defn get-projects
  "Fetches the active projects from harvest. Caches the harvest
  response for one day."
  [{::keys [data-dir] :as client}]
  (parse-projects
   (:projects
    (with-file-cache {:ttl (weeks 1)
                      :file (io/file data-dir "cache/projects.edn")}
      (:body (get-projects* client))))))

(defn get-project-tasks
  [{::keys [data-dir] :as client} project-id]
  (parse-tasks
   (:task_assignments
    (let [cache-key (format "cache/project_%s_tasks.edn" project-id)
          path (str "/projects/" project-id "/task_assignments")]
      (with-file-cache {:ttl (weeks 1) :file (io/file data-dir cache-key)}
        (:body (request client {:path path
                                :query-params {:is_active true}})))))))

(defn find-project
  "Fetches the projects and attempts to find a match in the client
  name and task name. Will throw when none found, pick the most recent
  one if multiple are found."
  [projects re]
  (let [candidates (filter (comp (partial re-find re) project-search-name) projects)
        pick (last (sort-by :project/updated-at candidates))]
    (when (empty? candidates)
      (throw (ex-info "Could not find project" {:name re})))
    (when (> (count candidates) 1)
      (println (format "Multiple projects found for: '%s'. Picked: [%s] %s"
                       re (:client/name pick) (:project/name pick))))
    pick))

(defn post-time-entry!
  "Posts the time entry to Harvest. Will try to find a task for the
  given project. Throws if no task is found"
  [client project entry]
  (let [task (first (get-project-tasks client (:project/id project)))]
    (when-not (:task/id task)
      (throw (ex-info "Could not find default task for project"
                      {:project-id (:project/id project)
                       :entry entry})))
    (println (format "Pushing time-entry:\n  Entry: %s\n  Project: [%s] %s"
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
