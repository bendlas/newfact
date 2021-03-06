;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;; ### Port of the cljs reader to clj
;; by bendlas

(ns newfact.reader
  (:refer-clojure :exclude [read read-string])
  (:require [clojure.string :as str]))

(defprotocol PushbackReader
  (read-count [reader] "Returns number of characters read so far")
  (read-char [reader] "Returns the next char from the Reader,
nil if the end of stream has been reached")
  (unread [reader ch] "Push back a single character on to the stream"))

(defn push-back-reader [rdr]
  (let [rdr (java.io.PushbackReader. rdr)
        cnt (atom 0)]
    (reify
      PushbackReader
      (read-char [_]
        (let [c (.read rdr)]
          (when-not (neg? c)
            (swap! cnt inc)
            (char c))))
      (unread [_ ch]
        (when ch
          (.unread rdr (int ch))
          (swap! cnt dec)))
      (read-count [_] @cnt)
      java.io.Closeable
      (close [_] (.close rdr)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; predicates
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- whitespace?
  "Checks whether a given character is whitespace"
  [ch]
  (or (Character/isWhitespace ch) (= \, ch)))

(defn- numeric?
  "Checks whether a given character is numeric"
  [ch]
  (Character/isDigit ch))

(defn- comment-prefix?
  "Checks whether the character begins a comment."
  [ch]
  (= \; ch))

(defn- number-literal?
  "Checks whether the reader is at the start of a number literal"
  [reader initch]
  (or (numeric? initch)
      (and (or (= \+ initch) (= \- initch))
           (numeric? (let [next-ch (read-char reader)]
                       (unread reader next-ch)
                       next-ch)))))

(declare read macros dispatch-macros)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; read helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; later will do e.g. line numbers...
(defn reader-error
  [rdr & msg]
  (throw (RuntimeException. (apply str msg))))

(defn macro-terminating? [ch]
  (and (not= ch \#) (not= ch \') (contains? macros ch)))

(defn read-token
  [rdr initch]
  (loop [sb (doto (StringBuilder.)
              (.append initch))
         ch (read-char rdr)]
    (if (or (nil? ch)
            (whitespace? ch)
            (macro-terminating? ch))
      (do (unread rdr ch) (. sb (toString)))
      (recur (do (.append sb ch) sb) (read-char rdr)))))

(defn skip-line
  "Advances the reader to the end of a line. Returns the reader"
  [reader _]
  (loop []
    (let [ch (read-char reader)]
      (if (or (= ch \n) (= ch \r) (nil? ch))
        reader
        (recur)))))

(def int-pattern (re-pattern "([-+]?)(?:(0)|([1-9][0-9]*)|0[xX]([0-9A-Fa-f]+)|0([0-7]+)|([1-9][0-9]?)[rR]([0-9A-Za-z]+)|0[0-9]+)(N)?"))
(def ratio-pattern (re-pattern "([-+]?[0-9]+)/([0-9]+)"))
(def float-pattern (re-pattern "([-+]?[0-9]+(\\.[0-9]*)?([eE][-+]?[0-9]+)?)(M)?"))

(defn- match-int
  [s]
  (let [groups (re-find int-pattern s)]
    (if (nth groups 2)
      0
      (let [negate (if (= \- (nth groups 1)) -1 1) 
            [n radix] (cond
                       (nth groups 3) [(nth groups 3) 10]
                       (nth groups 4) [(nth groups 4) 16]
                       (nth groups 5) [(nth groups 5) 8]
                       (nth groups 7) [(nth groups 7) (Integer/parseInt (nth groups 7))] 
                       :default [nil nil])]
        (if (nil? n)
          nil
          (* negate (Integer/parseInt n radix)))))))


(defn- match-ratio
  [s]
  (let [groups (re-find ratio-pattern s)
        numinator (nth groups 1)
        denominator (nth groups 2)]
    (/ (Integer/parseInt numinator) (Integer/parseInt denominator))))

(defn- match-float
  [s]
  (Float/parseFloat s))

(defn- match-number
  [s]
  (cond
   (re-matches int-pattern s) (match-int s)
   (re-matches ratio-pattern s) (match-ratio s)
   (re-matches float-pattern s) (match-float s)))

(def escape-char-map {\t \tab
                      \r \return
                      \n \newline
                      \\ \\
                      \" \"
                      \b \backspace
                      \f \formfeed})

(defn read-unicode-char
  [reader initch]
  (reader-error reader "Unicode characters not supported by reader (yet)"))

(defn escape-char
  [buffer reader]
  (let [ch (read-char reader)
        mapresult (get escape-char-map ch)]
    (if mapresult
      mapresult
      (if (or (= \u ch) (numeric? ch))
        (read-unicode-char reader ch)
        (reader-error reader "Unsupported escape charater: \\" ch)))))

(defn read-past
  "Read until first character that doesn't match pred, returning
   char."
  [pred rdr]
  (loop [ch (read-char rdr)]
    (if (pred ch)
      (recur (read-char rdr))
      ch)))

(defn read-delimited-list
  [delim rdr recursive?]
  (loop [a []]
    (let [ch (read-past whitespace? rdr)]
      (when-not ch (reader-error rdr "EOF"))
      (if (= delim ch)
        a
        (if-let [macrofn (get macros ch)]
          (let [mret (macrofn rdr ch)]
            (recur (if (= mret rdr) a (conj a mret))))
          (do
            (unread rdr ch)
            (let [o (read rdr true nil recursive?)]
              (recur (if (= o rdr) a (conj a o))))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; data structure readers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn not-implemented
  [rdr ch]
  (reader-error rdr "Reader for " ch " not implemented yet"))

(defn read-dispatch
  [rdr _]
  (let [ch (read-char rdr)
        dm (get dispatch-macros ch)]
    (if dm
      (dm rdr _)
      (reader-error rdr "No dispatch macro for " ch))))

(defn read-unmatched-delimiter
  [rdr ch]
  (reader-error rdr "Unmached delimiter " ch))

(defn read-list
  [rdr _]
  (apply list (read-delimited-list \) rdr true)))

(def read-comment skip-line)

(defn read-vector
  [rdr _]
  (read-delimited-list \] rdr true))

(defn read-map
  [rdr _]
  (let [l (read-delimited-list \} rdr true)]
    (when (odd? (count l))
      (reader-error rdr "Map literal must contain an even number of forms"))
    (apply hash-map l)))

(defn read-number
  [reader initch]
  (loop [buffer (doto (StringBuilder.)
                  (.append initch))
         ch (read-char reader)]
    (if (or (nil? ch) (whitespace? ch) (contains? macros ch))
      (do
        (unread reader ch)
        (let [s (. buffer (toString))]
          (or (match-number s)
              (reader-error reader "Invalid number format [" s "]"))))
      (recur (do (.append buffer ch) buffer) (read-char reader)))))

(defn read-literal-string
  [reader _]
  (loop [buffer (StringBuilder.)
         ch (read-char reader)]
    (cond
     (nil? ch) (reader-error reader "EOF while reading string")
     (= \\ ch) (recur (escape-char buffer reader) (read-char reader))
     (= \" ch) (. buffer (toString))
     :default (recur (do (.append buffer ch) buffer) (read-char reader)))))

(def special-symbols
  {"nil" nil
   "true" true
   "false" false})

(defn read-symbol
  [reader initch]
  (let [start (dec (read-count reader))
        token (read-token reader initch)
        end (read-count reader)
        sym (if (pos? (.indexOf token (int \/)))
              (symbol (subs token 0 (.indexOf token (int \/)))
                      (subs token (inc (.indexOf token (int \/))) (.length token)))
              (get special-symbols token (symbol token)))]
    (if (instance? clojure.lang.IObj sym)
      (with-meta sym
        (merge (meta sym) {:start start :end end}))
      sym)))

(defn read-keyword
  [reader initch]
  (let [token (read-token reader (read-char reader))]
    (if (pos? (.indexOf token (int \/)))
      (keyword (subs token 0 (.indexOf token (int \/)))
               (subs token (inc (.indexOf token (int \/))) (.length token)))
      (keyword token))))

(defn desugar-meta
  [f]
  (cond
   (symbol? f) {:tag f}
   (string? f) {:tag f}
   (keyword? f) {f true}
   :else f))

(defn wrapping-reader
  [sym]
  (fn [rdr _]
    (list sym (read rdr true nil true))))

(defn throwing-reader
  [msg]
  (fn [rdr _]
    (reader-error rdr msg)))

(defn read-meta
  [rdr _]
  (let [m (desugar-meta (read rdr true nil true))]
    (when-not (map? m)
      (reader-error rdr "Metadata must be Symbol,Keyword,String or Map"))
    (let [o (read rdr true nil true)]
      (if (instance? clojure.lang.IObj o)
        (with-meta o (merge (meta o) m))
        (reader-error rdr "Metadata can only be applied to IObjs")))))

(defn read-set
  [rdr _]
  (set (read-delimited-list \} rdr true)))

(defn read-regex
  [rdr ch]
  (loop [buffer (StringBuilder.)
         ch (read-char rdr)]
    (cond
     (nil? ch) (reader-error rdr "EOF while reading string")
     (= \\ ch) (do (.append buffer ch)
                   (.append buffer (read-char rdr)) ;; TODO check for EOF
                   (recur buffer (read-char rdr)))
     (= \" ch) (re-pattern (.toString buffer))
     :default (recur (do (.append buffer ch) buffer) (read-char rdr)))))

(defn read-discard
  [rdr _]
  (read rdr true nil true)
  rdr)

;; actually a non - implementation of syntax-quote/unquote
;; but good enough for now

(defn read-sq
  [rdr _]
  (read rdr true nil true))

(defn read-uq
  [rdr _]
  (read rdr true nil true))

(defn read-gensym [rdr ini]
  (read-symbol rdr ini)
  (gensym "%"))

(def macros
     { \" read-literal-string
       \: read-keyword
       \; not-implemented ;; never hit this
       \' (wrapping-reader 'quote)
       \@ (wrapping-reader 'deref)
       \^ read-meta
       \` read-sq
       \~ read-uq
       \( read-list
       \) read-unmatched-delimiter
       \[ read-vector
       \] read-unmatched-delimiter
       \{ read-map
       \} read-unmatched-delimiter
       \\ read-char
       \% read-gensym
       \# read-dispatch
       })

;; omitted by design: var reader, eval reader
(def dispatch-macros
  {\{ read-set
   \< (throwing-reader "Unreadable form")
   \" read-regex
   \! read-comment
   \_ read-discard
   \^ read-meta
   \( read-list})

(defn read
  "Reads the first object from a PushbackReader. Returns the object read.
   If EOF, throws if eof-is-error is true. Otherwise returns sentinel."
  [reader eof-is-error sentinel is-recursive]
  (let [ch (read-char reader)]
    (cond
     (nil? ch) (if eof-is-error (reader-error reader "EOF") sentinel)
     (whitespace? ch) (recur reader eof-is-error sentinel is-recursive)
     (comment-prefix? ch) (recur (read-comment reader ch) eof-is-error sentinel is-recursive)
     :else (let [res
                 (cond
                  (macros ch) ((macros ch) reader ch)
                  (number-literal? reader ch) (read-number reader ch)
                  :else (read-symbol reader ch))]
     (if (= res reader)
       (recur reader eof-is-error sentinel is-recursive)
       res)))))

(defn read-string
  "Reads one object from the string s"
  [s]
  (let [r (push-back-reader (java.io.StringReader. s))]
    (read r true nil false)))

  
