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
  (is (= [#:entry{:hours 0.25,
                  :project-handles ["brightmotive" "product"]
                  :_raw "20 May | Monday | 11:15 - 11:30 Standup",
                  :spent-at (t/date-time 2019 05 20)
                  :title "Standup"}
          #:entry{:hours 0.25,
                  :project-handles ["projectA"],
                  :_raw "20 May | Monday | 11:30 - 11:45 Review",
                  :spent-at (t/date-time 2019 05 20)
                  :title "Review"}
          #:entry{:hours 1.0,
                  :project-handles ["projectA"],
                  :_raw "20 May | Tuesday | 16:00 - 17:00 Emails",
                  :spent-at (t/date-time 2019 05 21)
                  :title "Emails"}
          #:entry{:hours 2.5,
                  :project-handles ["custA" "projectB"],
                  :_raw "20 May | Tuesday | 17:00 - 19:30 Programming",
                  :spent-at (t/date-time 2019 05 21)
                  :title "Programming"}]
         (sut/parse (org/->json "resources/example_timesheet.org")))))
