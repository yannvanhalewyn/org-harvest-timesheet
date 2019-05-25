(ns hs.utils
  (:require [cheshire.core :as json]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str])
  (:import org.jline.terminal.TerminalBuilder
           org.joda.time.DateTime
           java.io.File))

(defn keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))

(defn parse-int [s]
  (when-let [x (re-find #"^-?\d+$" (str s))]
    (Integer. ^String x)))

(defn read-json [s]
  (json/parse-string s keywordize))

(defn readable-date [d]
  (f/unparse (f/formatter "E MMM d, yyyy") d))

(defn assert-spec! [spec x]
  (when-not (s/valid? spec x)
    (throw (ex-info "Spec failed!"
                    {:spec spec
                     :failure (s/explain-str spec x)
                     :value x})))
  x)

(defn assert-spec+! [spec coll]
  (doseq [x coll] (assert-spec! spec x))
  coll)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Files

(defn home-dir
  "Returns the file at <path> in the home directory"
  [path]
  (if-let [home (System/getProperty "user.home")]
    (io/file home path)
    (throw (ex-info "No home directory found" {}))))

(defn file-exists? [^File file]
  (.exists file))

(defn- last-modified [^File file]
  (when (file-exists? file)
    (DateTime. (.lastModified file))))

(defn with-file-cache* [{:keys [ttl ^File file]} f]
  (if (or (not (file-exists? file))
          (t/after? (t/now) (t/plus (last-modified file) ttl)))
    (let [data (f)]
      (io/make-parents file)
      (spit (.getAbsolutePath file) (pr-str data))
      data)
    (read-string (slurp file))))

(defmacro with-file-cache [opts & body]
  `(with-file-cache* ~opts (fn [] ~@body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Logging / Console

(defn- escape-code [s]
  (str "\033[" s "m"))

(def ^:private COLORS
  (zipmap [:grey :red :green :yellow
           :blue :magenta :cyan :white]
          (map escape-code
               (range 30 38))))

(defn colorize [color msg]
  (str (COLORS color 0) msg (escape-code 0)))

(defn info [& args]
  (apply println args))

(defn error [& args]
  (apply println (colorize :red "Error") args))

(defn confirm! [msg]
  (let [term (.. (TerminalBuilder/builder)
                 (jna true) (system true) (build))
        r (.reader term)
        w (.writer term)]
    (.print w (str msg " [y/N] "))
    (.flush w)
    (.enterRawMode term)
    (let [result (.read r)]
      (.close r)
      (.close term)
      (println)
      (= 121 result))))
