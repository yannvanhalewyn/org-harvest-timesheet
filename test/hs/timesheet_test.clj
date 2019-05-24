(ns hs.timesheet-test
  (:require [clj-time.core :as t]
            [clojure.test :refer [are deftest is]]
            [hs.timesheet :as sut]
            [hs.org :as org]))

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

(deftest parse
  (is (= [#:entry{:hours 1.0
                  :title "QA + Depoy"
                  :project-handles ["projectA"]
                  :spent-at (t/date-time 2019 05 20)
                  :_raw "20 May | Monday | 17:00 - 18:00 QA + Depoy"}
          #:entry{:hours 0.25
                  :title "Standup"
                  :project-handles ["brightmotive" "product"]
                  :spent-at (t/date-time 2019 05 21)
                  :_raw "20 May | Tuesday | 10:30 - 10:45 Standup"}
          #:entry{:hours 1.0
                  :title "Fix bug"
                  :project-handles ["customerA" "bugs"]
                  :spent-at (t/date-time 2019 05 21)
                  :_raw "20 May | Tuesday | 11:30 - 12:30 Fix bug"}]
         (take 3 (drop 5 (sut/parse (org/->json "resources/example_timesheet.org")))))))
