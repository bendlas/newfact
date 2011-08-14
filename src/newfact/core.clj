(ns newfact.core
  (:require
   [clojure.java.io :as io] 
   (newfact
    [reader :as r]
    [find :as f])))

(defn form-seq [rdr]
  (lazy-seq
   (let [form (r/read rdr false ::EOF false)]
     (when-not (identical? form ::EOF)
       (cons form (form-seq rdr))))))

(defn rename [in n ref to]
  (let [analysis
        (with-open [rdr (r/push-back-reader (io/reader in))]
          (reduce (fn [found form]
                    (into found
                          (map #(assoc (select-keys (meta %) [:start :end])
                                  :sym %)
                               (f/references form ref n))))
                  []
                  (form-seq rdr)))]
    analysis))

(rename "src/newfact/core.clj" *ns* 'rename 'rewuschel)
