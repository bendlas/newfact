(ns cljs.test.compiler
  (:refer-clojure :exclude [munge load-file loaded-libs macroexpand-1])
  (:use cljs.compiler)
  (:use [newfact.test.utils :only [is]]
        [lazytest.describe :only [describe it]]))

(def env {:ns {:name 'user}
          :context :expr
          :locals {}})

(defn select-subtree [super sub]
  (cond
   (map? sub)
   (into {} (for [[k v] sub
                  :when (contains? super k)
                  :let [sv (super k)]]
              [k (select-subtree sv v)]))

   (vector? sub)
   (vec (map select-subtree super sub))

   (seq? sub)
   (map select-subtree super sub)

   :else super))

(defn map-contains? [super sub]
  (= sub
     (select-subtree super sub)))

(describe
 "Analyze simple forms"
 (it "tests symbol occurrences"
     (is map-contains? (analyze env 'x) '{:info {:name user.x} :form x})))

(describe
 "Analyze special forms"
 (it "tests let"
     (is map-contains? (analyze env '(let [x 1] x))
         '{:env {:ns {:name user}, :context :expr, :locals {}},
           :op :let,
           :loop false,
           :bindings
           [{:init {:op :constant,
                    :env {:ns {:name user}, :context :expr, :locals {}},
                    :form 1}}],
           :statements nil,
           :ret
           {:info
            {:init
             {:op :constant,
              :env {:ns {:name user}, :context :expr, :locals {}},
              :form 1}},
            :op :var,
            :env
            {:ns {:name user},
             :context :return},
            :form x},
           :form (let* [x 1] x)})))
