(ns hs.harvest
  (:require [clj-http.client :as http]
            [clj-time.core :refer [days]]
            [clj-time.format :as f]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hs.file :as file]
            [hs.utils :refer [with-file-cache]]))

(defn- parse-date [d]
  (f/parse (f/formatter :date-time-no-ms) d))

(defn- clean-string [s]
  (str/lower-case (str/replace s #"[^a-zA-Z0-9]" "")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

(defn- project-search-name [p]
  (clean-string (str (:client/name p) (:project/name p))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client

(def ^:private BASE_URL "https://api.harvestapp.com/api/v2")

(defn- request [{::keys [api-token account-id]} {:keys [method path params query-params]}]
  (http/request
   {:url (str BASE_URL path)
    :method (or method :post)
    :headers {"Harvest-account-id" account-id
              "Authorization" (str "Bearer " api-token)}
    :params params
    :query-params query-params
    :as :json}))

(defn- get-projects* [client]
  (println "Fetching harvest projects")
  (request client {:path "/projects.json"
                   :method :get
                   :query-params {:is_active true}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defn make-client
  "Reads the api-token and account-id from the env"
  []
  {::api-token (System/getenv "HARVEST_API_TOKEN")
   ::account-id (System/getenv "HARVEST_ACCOUNT_ID")
   ::data-dir (file/home ".harvest_sync")})

(defn get-projects
  "Fetches the active projects from harvest. Caches the harvest
  response for one day."
  [{::keys [data-dir] :as client}]
  (parse-projects
   (:projects
    (with-file-cache {:ttl (days 1)
                      :file (io/file data-dir "cache/projects.edn")}
      (:body (get-projects* client))))))

(defn find-project!
  "Fetches the projects and attempts to find a match in the client
  name and task name. Will throw when none found, pick the most recent
  one if multiple are found."
  [client q]
  (let [candidates (filter
                    #(str/includes? (project-search-name %) (clean-string q))
                    (get-projects client))
        pick (last (sort-by :project/updated-at candidates))]
    (when (empty? candidates)
      (throw (ex-info "Could not find project" {:name q})))
    (when (> (count candidates) 1)
      (println (format "Multiple projects found for: '%s'. Picked: [%s] %s"
                       q (:client/name pick) (:project/name pick))))
    pick))
