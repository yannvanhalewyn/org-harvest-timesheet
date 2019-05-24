(ns hs.timesheet-test
  (:require [hs.timesheet :as sut]
            [clojure.test :refer [deftest is are]]
            [clj-time.core :as t]))

(deftest parse-entry-title
  (are [in out] (= out (sut/parse-entry-title in))
    "11:30 - 12:30 Foobar" [1.0 "Foobar"]
    "11 - 12 Foobar" [1.0 "Foobar"]
    "16:45 - 18 Foobar" [1.25 "Foobar"]))

(deftest parse-weekday
  (are [a b m d] (= (t/date-time (t/year (t/now)) m d) (sut/parse-weekday a b))
    "20 May" "Monday" 5 20
    "20 May" "Tuesday" 5 21
    "1 jul" "thursday" 7 4))
