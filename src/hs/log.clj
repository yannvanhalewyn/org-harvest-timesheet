(ns hs.log)

(defn- escape-code [s]
  (str "\033[" s "m"))

(def ^:private COLORS
  (zipmap [:grey :red :green :yellow
           :blue :magenta :cyan :white]
          (map escape-code
               (range 30 38))))

(defn color [color msg]
  (str (COLORS color) msg (escape-code 0)))

(defn info [& args]
  (apply println args))

(defn error [& args]
  (apply println (color :red "[ERROR]") args))
