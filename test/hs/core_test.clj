(ns hs.core-test
  (:require [hs.core :as sut]
            [clojure.test :refer [deftest is are]]))

(deftest parse-entry-value
  (are [in out] (= out (sut/parse-entry-value in))
    "11:30 - 12:30 Foobar" [1.0 "Foobar"]
    "11 - 12 Foobar" [1.0 "Foobar"]
    "16:45 - 18 Foobar" [1.25 "Foobar"]))
