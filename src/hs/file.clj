(ns hs.file
  (:require [clj-time.core :as t]
            [clojure.java.io :as io])
  (:import org.joda.time.DateTime
           java.nio.file.Path))

(defn home
  "Returns the file at <path> in the home directory"
  [path]
  (if-let [home (System/getProperty "user.home")]
    (io/file (str home "/" path))
    (throw (ex-info "No home directory found" {}))))

(defn exists? [file]
  (.exists file))

(defn last-modified [file]
  (when (exists? file) (DateTime. (.lastModified file))))
