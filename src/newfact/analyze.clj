(ns newfact.analyze
  (:use newfact.util))

(comment
  #{'def
    'loop*
    'recur
    'if
    'let*
    'letfn*
    'do
    'fn*
    'quote
    'var
    'set!
    'try
    'catch
    'finally
    'throw
    'monitor-enter
    'monitor-exit
    'import
    'deftype*
    'case*
    'new
    '.})

(defrecord CompilationContext
    [namespaces ns env quote-level])

(defprotocol Form
  (references* [form compilation-context]
    "Get a map of occurring free symbols to binding locations")
  (locate [form]
    "Create a location descriptor from form with reader metadata"))

(defn references
  "Duplicates may occur due to macro expansion"
  ([form] (references form *ns*))
  ([form n] (references* form (CompilationContext. {} n {} 0))))

(defmulti references-invoke-sym
  "Analyze forms that are calls. Can be either plain calls, macro invocations or special forms"
  (fn [[call-sym & _] _]
    call-sym))

(defn references-seq
  "Analyze a sequence of forms"
  [seq ctx]
  (mapcat #(references* % ctx) seq))

;; Base implementations
(extend-protocol Form
  clojure.lang.ISeq
  (references* [[call-pos & args :as form] {:keys [ns env] :as ctx}]
    (cond
     ; non symbol calls are scanned as sequence
     (not (symbol? call-pos))
     (references-seq form ctx)

     ; macros are expanded
     (-> (ns-resolve ns call-pos) meta :macro)
     (recur
      (apply @(ns-resolve ns call-pos) form env args)
      ctx)

     ; symbol calls are going into a multimethod to handle specials
     :else
     (references-invoke-sym form ctx)))
  clojure.lang.Symbol
  (references* [sym {:keys [ns env quote-level]}]
    (if (zero? quote-level)
      [(assoc (locate sym)
         :symbol sym
         :bound-to (or (env sym) (ns-resolve ns sym)))]
      [(locate sym)]))
  (locate [sym]
    (select-keys (meta sym)
                 [:start :end])))

(def no-references (constantly []))
(def no-location (constantly {}))

((fn [&{:as impls}]
   (doseq [[T [ref loc]] impls]
     (extend T
       Form {:references* ref
             :locate loc})))
 nil [no-references no-location]
 Object [no-references no-location]
 clojure.lang.IPersistentCollection [references-seq no-location])

(defn let-binding [binding init]
  (assoc (locate binding)
    :init init))

(defn fn-binding [arg]
  (locate arg))

(defn field-binding [f]
  (locate f))

(defn fn-label [lbl]
  (locate lbl))

(defn assoc-var [{ns :ns :as ctx} name]
  (intern ns name)
  ctx)

(defn assoc-local
  [ctx sym desc]
  (assoc-in ctx [:env sym] desc))

(defn assoc-locals
  [ctx symbols desc-fn]
  (reduce (fn [ctx sym]
            (assoc-local ctx sym (desc-fn sym)))
          ctx symbols))

(defn references-let ; 'let and 'loop
  [[_ bindings & body] ctx]
  (loop [[binding init & rest] bindings
         ctx ctx
         found []]
    (if binding
      (let [ctx (assoc-local ctx binding (let-binding binding init))]
        (recur rest ctx               
               (into found (references* init ctx))))
      (into found (references-seq body ctx)))))

(doto references-invoke-sym
  (.addMethod 'let* references-let)
  (.addMethod 'loop* references-let))

(defn references-fndef [[name-args-impl? & tail :as forms] ctx] ; 'fn 'letfn 
  (letfn [(impl [args body]
            (references-seq body (assoc-locals ctx args fn-binding)))]
    (cond (symbol? name-args-impl?)
          (recur tail (assoc-local ctx name-args-impl? (fn-label name-args-impl?)))
          
          (vector? name-args-impl?)
          (impl name-args-impl? tail)

          :else
          (mapcat impl
                  (map first forms)
                  (map rest forms)))))

(defmethod references-invoke-sym 'fn*
  [[_  & fntail] ctx]
  (references-fndef fntail ctx))

(defn references-fnseq [fns ctx]
  (mapcat #(references-fndef % ctx)
          fns))

(defmethod references-invoke-sym 'letfn*
  [[_ bindings & body] ctx]
  ;; first put every function name into local context
  (let [ctx (assoc-locals ctx (map first bindings) fn-binding)]
    (concat (references-fnseq bindings ctx)
            (references-seq body ctx))))

(defmethod references-invoke-sym 'catch
  [[_ _ local & body] ctx]
  (references-seq body (assoc-local ctx local (fn-binding local))))

(defmethod references-invoke-sym 'set!
  [[_ loc expr] ctx]
  (if (symbol? loc)
    (concat (references* loc ctx)
            (references* expr ctx))))

(defmethod references-invoke-sym 'deftype*
  [[_ _ _ fields & o+m] ctx]
  (loop [tail o+m]
    (if (-> tail first keyword?) ; an option, skip it TODO: protocol renaming
      (recur (nnext tail))
      (references-fnseq tail (assoc-locals ctx fields field-binding)))))

(defmethod references-invoke-sym 'reify*
  [[_ _ & methods] ctx] ;; TODO : protocol renaming
  (references-fnseq methods ctx))

(defmethod references-invoke-sym 'quote
  [[_ & body] ctx]
  (references-seq body (update-in ctx [:quote-level] inc)))

(defmethod references-invoke-sym 'def
  [[_ name & doc-init?] ctx]
  (let [ctx (assoc-var ctx name)]
    (case (count doc-init?)
      1 (references* (first doc-init?) ctx)
      2 (references* (second doc-init?) ctx)
      no-references)))

(defmethod references-invoke-sym :default
  [form ctx]
  (references-seq form ctx))



