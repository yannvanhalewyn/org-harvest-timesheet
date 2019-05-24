(ns hs.core-test
  (:require [hs.core :as sut]
            [clojure.test :refer [deftest is are]]))

(deftest parse-entry-value
  (are [in out] (= out (sut/parse-entry-value in))
    "11u30 - 12u30: Foobar" [1.0 "Foobar"]
    "11u - 12u: Foobar" [1.0 "Foobar"]
    "16u45 - 18u: Foobar" [1.25 "Foobar"]))
