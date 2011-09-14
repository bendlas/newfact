(ns newfact.utils
  (:require [clojure.contrib.mock :only (report-problem expect)])
  (:import [java.lang AssertionError])
  (:use [clojure.contrib.def :only (defmacro-)]
        [com.georgejahad.difform :only (difform)]))

(defn- throw-error [func actual expected & msg]
  (throw (AssertionError. 
    (str (if msg 
             (str (first msg) " ")
           "")
         "verification function is: " func "\nexpected: " (if (nil? expected) "nil" expected) "\n but got: " (if (nil? actual) "nil" actual) "\ndiff: " (with-out-str (difform expected actual))))))

(defn is
  ([func actual]
    (if (not (func actual))
        (throw (AssertionError. (str "verification function is: " func "\nwith value " (if (nil? actual) "nil" actual) "\nis false.")))
      true))
  ([func actual expected]
    (if (not (func actual expected))
        (throw-error func actual expected)
      true)))

(defn assert-and-report-problem
  {:dynamic true}
  ([function expected actual]
    (assert-and-report-problem function expected actual "Expectation not met."))
  ([function expected actual message]
    (throw-error function actual expected message)))

; Borrowed from clojure core. Remove if this ever becomes public there.
(defmacro- assert-args
    [fnname & pairs]
    `(do (when-not ~(first pairs)
        (throw (IllegalArgumentException. ~(str fnname " requires " (second pairs)))))
      ~(let [more (nnext pairs)]
        (when more
          (list* `assert-args fnname more)))))

(defn #^{:private true} make-bindings [expect-bindings mock-data-sym]
    `[~@(interleave (map #(first %) (partition 2 expect-bindings))
           (map (fn [i] `(nth (nth ~mock-data-sym ~i) 0))
         (range (quot (count expect-bindings) 2))))])

(defmacro expect [expect-bindings & body]
  (assert-args expect
    (vector? expect-bindings) "a vector of expectation bindings"
    (even? (count expect-bindings)) "an even number of forms in expectation bindings")
    (let [mock-data (gensym "mock-data_") result (gensym "result_") mock-result (gensym "mock-result_")]
      `(with-redefs [clojure.contrib.mock/report-problem assert-and-report-problem]
        (let [~mock-data (map (fn [args#] 
                (apply clojure.contrib.mock/make-mock args#))
              ~(cons 'list (map (fn [[n m]] (vector (list 'quote n) m))
                          (partition 2 expect-bindings)))) 
              ~result (with-redefs ~(make-bindings expect-bindings mock-data) ~@body)]
          (clojure.contrib.mock/validate-counts ~mock-data)
          ~result))))
