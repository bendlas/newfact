(ns newfact.core
  (:require
   [clojure.java.io :as io] 
   (newfact
    [reader :as r]
    [find :as f])))

(def found (lazy-seq (cons found nil)))

(defn form-seq [rdr]
  (lazy-seq
   (let [form (r/read rdr false ::EOF false)]
     (when-not (identical? form ::EOF)
       (cons form (form-seq rdr))))))

(defn rename
  "Print 'in' file with occurrences of resolvable (according to namespace 'n')
symbol 'ref' replaced by 'to'."
  [in n ref to]
  (let [buf (char-array 1024)
        analysis
        (with-open [rdr (r/push-back-reader (io/reader in))]
          (reduce (fn [found form]
                    (into found
                          (map #(assoc (select-keys (meta %) [:start :end])
                                  :sym %)
                               (f/references form ref n))))
                  #{}
                  (form-seq rdr)))]
    (with-open [rdr (io/reader in)]
      (loop [[{:keys [start end] :as found} & rest :as all]
             (sort-by :start analysis)
             
             pos 0]
        (if found
          (let [diff (- start pos)
                len (- end start)]
            (if (zero? diff)
              (do
                (print to)
                (.skip rdr len)
                (recur rest (+ pos len)))
              (let [cnt (.read rdr buf 0 diff)]
                (.write *out* buf 0 cnt)
                (recur all (+ pos cnt)))))
          (io/copy rdr *out*))))))

#_(rename "src/newfact/core.clj" *ns* 'found 'rewuschel)
