(ns hs.harvest
  (:require [clj-http.client :as http]
            [clj-time.format :as f]
            [clj-time.core :refer [days]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hs.file :as file]
            [hs.utils :refer [with-file-cache]]))

(def parse-date (partial f/parse (f/formatter :date-time-no-ms)))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

(defn get-projects [{::keys [data-dir] :as client}]
  (parse-projects
   (:projects
    (with-file-cache {:ttl (days 1)
                      :file (io/file data-dir "cache/projects.edn")}
      (:body (get-projects* client))))))
