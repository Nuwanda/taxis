(defproject taxis "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 ;;Client dependencies
                 [org.clojure/clojurescript "0.0-2342"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [om "0.7.3"]
                 [prismatic/om-tools "0.3.6" :exclusions [org.clojure/clojure]]
                 [secretary "1.2.1"]
                 ;;Server dependencies
                 [compojure "1.1.5"]
                 [ring/ring-devel "1.1.8"]
                 [ring/ring-core "1.1.8"]
                 [http-kit "2.0.0"]
                 [clj-json "0.5.3"]
                 ;;server/client ws over channels
                 [jarohen/chord "0.4.2" :exclusions [org.clojure/clojure]]]

  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]]

  :min-lein-version "2.0.0"

  :main "taxis.server"

  :source-paths ["src/clj"]

  :cljsbuild { 
    :builds [{:id "taxis"
              :source-paths ["src/cljs"]
              :compiler {
                :output-to "out/taxis.js"
                :output-dir "out"
                :optimizations :none
                :source-map true}}]})
