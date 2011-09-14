(require '[clojure.java.io :as io])

(defn tools-jar []
  (.getCanonicalPath (io/file (System/getProperty "java.home")
                              ".." "lib" "tools.jar")))
(defproject newfact "0.0.1"
  :description "A refactoring tool for clojure code"
  :repositories {"stuartsierra-releases" "http://stuartsierra.com/maven2"
                 "platogo" "http://banana.platogo.com:8080/nexus/content/repositories/thirdparty/"}

  :dependencies [;; Core
                 [org.clojure/clojure "1.3.0-beta2"]

                 ;; Logging
                 [org.clojure/tools.logging "0.2.0"]
                 [ch.qos.logback/logback-classic "0.9.29"]]
  :dev-dependencies [[swank-clojure "1.4.0-SNAPSHOT"]
		     [com.stuartsierra/lazytest "1.2.3" :exclusions [org.clojure/clojure org.clojure/clojure-contrib]]
		     [org.clojars.brenton/difform "1.0.2-PATCH" :exclusions [org.clojure/clojure org.clojure/clojure-contrib]]]
  :jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]

  :extra-classpath-dirs [#=(tools-jar)])
