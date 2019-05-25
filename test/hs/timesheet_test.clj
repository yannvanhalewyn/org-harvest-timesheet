(ns hs.timesheet-test
  (:require [clj-time.core :as t]
            [clojure.test :refer [deftest testing is are]]
            [hs.org :as org]
            [hs.timesheet :as sut]))

(deftest parse-entry-title
  (are [in out] (= out (sut/parse-entry-title in))
    "11:30 - 12:30 Foobar" [1.0 "Foobar"]
    "11 - 12 Foobar" [1.0 "Foobar"]
    "16:45 - 18 Foobar" [1.25 "Foobar"]
    "8:45 - 9 Mails" [0.25 "Mails"]))

(deftest parse-weekday
  (are [a b m d] (= (t/date-time (t/year (t/now)) m d) (sut/parse-weekday a b))
    "20 May" "Monday" 5 20
    "20 May" "Tuesday" 5 21
    "1 jul" "thursday" 7 4))

(def ALL
  [#:entry{:hours 0.5
           :title "Standup + discussion"
           :project-handles ["default" "project"]
           :spent-at (t/date-time 2019 05 14)}
   #:entry{:hours 0.25
           :title "Standup"
           :project-handles ["default" "project"]
           :spent-at (t/date-time 2019 05 20)}
   #:entry{:hours 0.5
           :title "Emails"
           :project-handles ["projectA"]
           :spent-at (t/date-time 2019 05 20)}
   #:entry{:hours 1.75
           :title "Programming"
           :project-handles ["projectA"]
           :spent-at (t/date-time 2019 05 20)}
   #:entry{:hours 0.25
           :title "Standup"
           :project-handles ["default" "project"]
           :spent-at (t/date-time 2019 05 21)}
   #:entry{:hours 1.0
           :title "Fix bug"
           :project-handles ["customerA" "bugs"]
           :spent-at (t/date-time 2019 05 21)}])

(deftest parse
  (let [data (org/->json "resources/example_timesheet.org")
        opts {:default-project ["default" "project"]}]
    (testing "Can read org files and parse the entries"
      (is (= ALL (sut/parse data opts))))

    (testing "It takes a week str"
      (is (= (take 1 ALL) (sut/parse data (assoc opts :week "13 may")))))

    (testing "It taks a :last week option"
      (is (= (drop 1 ALL) (sut/parse data (assoc opts :week :last)))))))
