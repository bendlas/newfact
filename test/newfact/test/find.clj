(ns newfact.test.find
  (:require [clojure.string :as str])
  (:use [newfact.find])
  (:use [clojure.test]))

(in-ns 'foreign)

(def needle)
(def stray)

(in-ns 'home)
(clojure.core/alias 'f 'foreign)
(clojure.core/refer-clojure)
(clojure.core/refer 'foreign :only ['needle])
(def stray)

(in-ns 'newfact.test.find)

(defn expect-meta-keys [form sym n & keys]
  (testing (format "Form: %s, Needle: %s, Namespace: %s"
                   form sym n)
    (let [result (references form sym n)
          result-metas (map-indexed (fn [i k]
                                      (when (contains? (meta (nth result i)) k)
                                        k))
                                    keys)]
      (testing (format "Result: %s against %s"
                       (str/join " " (map meta result))
                       (str/join " " keys))
        (is (= (count result) (count keys)))
        (is (every? keyword? result-metas))))))

(def stray-form
  '(do (^:sh1 stray form)
       (let [stray (init ^:sf1 f/stray)]
         ^:sf2 foreign/stray
         stray)
       ^:sn1 needle
       (defn f [needle]
         '(stray ^:sh2 home/stray needle)
         (map ^:sh3 stray needle ^:sn2 f/needle)
         (fn [stray] (needle stray)))
       (for [stray ^:sh4 stray
             :let [x (foo ^:sn3 needle)
                   needle stray
                   y (no needle)]]
         (^:sf3 f/stray stray needle))))

(deftest binding-forms
  (expect-meta-keys stray-form 'foreign/stray 'home
                    :sf1 :sf2 :sf3 :sf3)
  (expect-meta-keys stray-form 'home/stray 'home
                    :sh1 :sh2 :sh3 :sh4)
  (expect-meta-keys stray-form 'foreign/needle 'home
                    :sn1 :sn2 :sn3 :sn3))
