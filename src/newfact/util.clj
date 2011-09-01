(ns newfact.util
  (:use [clojure.tools.logging :only [debug spy]]

        clojure.pprint)

         
  (:require [clojure.java.io :as io]
            [clojure.reflect :as refl]))

(defmacro throwf [E msg & parms]
  `(throw (new ~E (format ~msg ~@parms))))

(def magic)
(defmacro peek-env []
  (alter-var-root #'magic
                  (constantly [&form &env]))
  nil)
(let [moo 5] (peek-env))

(defn public-fields
  ([o] (public-fields o false))
  ([o with-types?]
     (->> (:members (refl/reflect o))
          (filter (partial instance? clojure.reflect.Field))
          (filter (comp :public :flags))
          (remove (comp :static :flags))
          (map (fn [{:keys [name type]}]
                 [(if with-types?
                    (keyword (str type) (str name))
                    (with-meta name ;(-> name str keyword)
                      {:tag type}))
                  (-> o class
                      (.getField (str name))
                      (.get o))]))
          (into {}))))
