(require '[clojure.java.io :as io])

(defn tools-jar []
  (.getCanonicalPath (io/file (System/getProperty "java.home")
                              ".." "lib" "tools.jar")))
(defproject newfact "0.0.1"
  :description "A refactoring tool for clojure code"
  :dependencies [;; Core
                 [org.clojure/clojure "1.3.0-beta2"]

                 ;; Logging
                 [org.clojure/tools.logging "0.2.0"]
                 [ch.qos.logback/logback-classic "0.9.29"]]
  :dev-dependencies [[swank-clojure "1.4.0-SNAPSHOT"]]
  :jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]

  :extra-classpath-dirs [#=(tools-jar)])
