(ns newfact.find)

(defmacro throwf [E msg & parms]
  `(throw (new ~E (format ~msg ~@parms))))

(defn not-found [sym]
  (throwf RuntimeException "Can't find %s in current context" sym))

(defprotocol Form
  (references* [form v locals namespace]))

(defn references
  ([form sym] (references form sym *ns*))
  ([form sym n]
     (if-let [v (resolve sym)]
       (references* form v #{} n)
       (not-found sym))))

(defn in-seq [body v l n]
  (mapcat #(references* % v l n)
          body))

(defmulti in-call
  (fn [form v l n]
    (let [f (first form)]
      (if (and (symbol? f)
               (-> (ns-resolve n l f) meta :macro))
        ::macro f))))

(defmethod in-call ::macro
  [[call & args :as form] v l n]
  (references* (apply @(ns-resolve n l call) form nil
                   args)
            v l n))

(defn in-let
  [[_ bindings & body] v l n]
  (loop [[binding init & rest] bindings
         locals l
         found []]
    (if binding
      (recur rest
             (conj locals binding)
             (into found (references* init v locals n)))
      (into found (in-seq body v locals n)))))

(doto in-call
  (.addMethod 'let* in-let)
  (.addMethod 'loop* in-let))

(defn in-fndef [[name-args-impl? & tail :as forms] v locals n]
  (letfn [(impl [args body]
            (in-seq body v (into locals args) n))]
    (cond (symbol? name-args-impl?)
          (recur tail v (conj locals name-args-impl?) n)
          
          (vector? name-args-impl?)
          (impl name-args-impl? tail)

          :else
          (mapcat impl
                  (map first forms)
                  (map rest forms)))))

(defn in-fnseq [fns v l n]
  (mapcat #(in-fndef % v l n)
          fns))

(defmethod in-call 'fn*
  [[_  & fntail] v locals n]
  (in-fndef fntail v locals n))

(defmethod in-call 'letfn*
  [[_ bindings & body] v locals n]
  (let [l (into locals (map first bindings))]
    (concat (in-fnseq bindings v l n)
            (in-seq body v l n))))

(defmethod in-call 'catch
  [[_ _ local & body] v l n]
  (in-seq body v (conj l local) n))

(defmethod in-call 'set!
  [[_ loc _] v l n]
  (if (symbol? loc)
    (references* loc v l n)))

(defmethod in-call 'deftype*
  [[_ _ _ fields & o+m] v l n]
  (loop [tail o+m]
    (if (-> tail first keyword?)
      (recur (nnext tail))
      (in-fnseq tail v (into l fields) n))))

(defmethod in-call 'reify*
  [[_ & methods] v l n]
  (mapcat #(in-fndef % v l n)
          methods))

(defmethod in-call 'quote
  [[_ & body] v _ n]
  (in-seq body v
               (reify clojure.lang.IPersistentSet
                 (contains [_ sym]
                   (nil? (namespace sym)))
                 (cons [this _] this))
               n))

(defmethod in-call :default
  [form v l n]
  (in-seq form v l n))

(def no-renames (constantly []))

((fn [&{:as impls}]
   (doseq [[T f] impls]
     (extend T
       Form
       {:references* f})))
 nil no-renames
 Object no-renames
 clojure.lang.ISeq in-call
 clojure.lang.IPersistentCollection in-seq)

(extend-protocol Form
  clojure.lang.Symbol
  (references* [this v locals n]
    (if (= v (ns-resolve n locals this))
      [this] []))
  clojure.lang.APersistentMap
  (references* [m v locals n]
    (in-seq (concat (keys m) (vals m))
                 v locals n)))

