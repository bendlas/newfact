(ns newfact.test.core
  (:use [newfact.core])
  (:use [clojure.test]))

(in-ns 'sandbox)
(clojure.core/refer-clojure)
(def needle)
(in-ns 'newfact.test.core)

(def code
  (.toCharArray "(needle (let [needle needle] needle))"))

(deftest test-rename
  (is (= "(noodle (let [needle noodle] needle))"
         (with-out-str 
           (rename code (find-ns 'sandbox) 'needle 'noodle)))))
