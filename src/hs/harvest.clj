(ns hs.harvest
  (:require [clj-http.client :as http]
            [clj-time.core :as t :refer [weeks]]
            [clj-time.format :as f]
            [clojure.java.io :as io]
            [hs.utils :as u :refer [parse-int with-file-cache]]))

(defn- parse-date [d]
  (f/parse (f/formatter :date-time-no-ms) d))

(defn- timestamp [d]
  (f/unparse (f/formatter :date-time-no-ms) d))

(defn- log [msg]
  (u/info (u/colorize :yellow "Harvest") msg))

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
  (for [{:keys [project client] :as r} records]
    {:entry/id (:id r)
     :entry/title (:notes r)
     :entry/spent-at (f/parse (f/formatter :date) (:spent_date r))
     :entry/hours (:hours r)
     :entry/locked? (:is_locked r)
     :project/name (:name project)
     :project/id (:id project)
     :client/name (:name client)
     :client/id (:id client)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client

(def ^:private BASE_URL "https://api.harvestapp.com/api/v2")

(defn- request [{::keys [access-token account-id]}
                {:keys [url method path params query-params]}]
  (:body
   (http/request
    {:url (or url (str BASE_URL path))
     :method (or method :get)
     :headers {"Harvest-account-id" account-id
               "Authorization" (str "Bearer " access-token)}
     :form-params params
     :query-params query-params
     :as :json})))

(def MAX_PAGES 10)
(defn- paginate [merge-key client params]
  (loop [results [(request client params)]
         i 1]
    (if-let [next (and (not (>= i MAX_PAGES))
                       (get-in (last results) [:links :next]))]
      (recur (conj results (request client (assoc params :url next))) (inc i))
      (mapcat merge-key results))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defn make-client
  "Reads the access-token and account-id from the env"
  [& [{:keys [harvest-access-token harvest-account-id]}]]
  (let [token (or harvest-access-token (System/getenv "HARVEST_ACCESS_TOKEN"))
        account-id (or harvest-account-id
                       (parse-int (System/getenv "HARVEST_ACCOUNT_ID")))]
    (when (or (not token) (not account-id))
      (throw (ex-info "No harvest access token or account-id supplied" {})))
    {::access-token token
     ::account-id account-id
     ::data-dir (u/home-dir ".harvest_sync")}))

(defn get-projects
  "Fetches the active projects from harvest. Caches the harvest
  response for one day."
  [{::keys [data-dir] :as client}]
  (parse-projects
   (with-file-cache {:ttl (weeks 1) :file (io/file data-dir "cache/projects.edn")}
     (log "Fetching projects")
     (paginate :projects client
               {:path "/projects.json" :query-params {:is_active true}}))))

(defn get-project-tasks
  "Gets the tasks for a given project-id from harvest"
  [{::keys [data-dir] :as client} project-id]
  (parse-tasks
   (let [cache-key (format "cache/project_%s_tasks.edn" project-id)
         path (str "/projects/" project-id "/task_assignments")]
     (with-file-cache {:ttl (weeks 1) :file (io/file data-dir cache-key)}
       (log (str "Fetching tasks for project " project-id))
       (paginate :task_assignments client
                 {:path path :query-params {:is_active true}})))))

(defn get-entries
  "Fetches the entries between from and to from harvest"
  [client {:keys [from to]}]
  (assert (or (t/equal? from to) (t/before? from to)))
  (log (format "Fetching existing entries between %s and %s"
               (u/readable-date from) (u/readable-date to)))
  (let [user (request client {:path "/users/me"})]
    (assert (:id user))
    (parse-entries
     (paginate :time_entries client
               {:path "/time_entries"
                :query-params {:from (timestamp from)
                               :user_id (:id user)
                               :to (timestamp to)}}))))

(defn delete-entry!
  "Deletes the time entry from Harvest"
  [client entry-id]
  (request client {:path (str "/time_entries/" entry-id)
                   :method :delete}))

(defn create-entry!
  "Posts the time entry to Harvest."
  [client entry]
  (request
   client
   {:path "/time_entries"
    :method :post
    :params {:project_id (:project/id entry)
             :task_id (:task/id entry)
             :spent_date (timestamp (:entry/spent-at entry))
             :hours (:entry/hours entry)
             :notes (:entry/title entry)}}))
