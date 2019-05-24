(ns hs.file
  (:require [clojure.java.io :as io])
  (:import org.joda.time.DateTime))

(defn home
  "Returns the file at <path> in the home directory"
  [path]
  (if-let [home (System/getProperty "user.home")]
    (io/file home path)
    (throw (ex-info "No home directory found" {}))))

(defn exists? [file]
  (.exists file))

(defn last-modified [file]
  (when (exists? file) (DateTime. (.lastModified file))))
