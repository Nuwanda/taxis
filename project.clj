(defproject taxis "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 ;;Client dependencies
                 [org.clojure/clojurescript "0.0-2371" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [om "0.7.3"]
                 [prismatic/om-tools "0.3.6" :exclusions [org.clojure/clojure]]
                 [secretary "1.2.1"]
                 ;;Cljs REPL
                 [com.cemerick/piggieback "0.1.3"]
                 [weasel "0.4.0-SNAPSHOT"]
                 ;;Server dependencies
                 [compojure "1.1.5"]
                 [ring/ring-devel "1.1.8"]
                 [ring/ring-core "1.1.8"]
                 [http-kit "2.0.0"]
                 [environ "1.0.0"]
                 ;;DB dependencies
                 [korma "0.3.0"]
                 [postgresql "9.3-1102.jdbc41"]
                 [org.clojure/java.jdbc "0.3.6"]
                 ;;server/client ws over channels
                 [jarohen/chord "0.4.2" :exclusions [org.clojure/clojure]]]

  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]
            [lein-environ "1.0.0"]]

  :min-lein-version "2.5.0"

  :uberjar-name "taxis.jar"

  :main taxis.server

  :source-paths ["src/clj" "src/cljs"]

  :cljsbuild {
              :builds [{:id "taxis"
                        :source-paths ["src/cljs"]
                        :compiler {
                                   :output-to "out/taxis.js"
                                   :output-dir "out"
                                   :optimizations :none
                                   :source-map true}}]}
  :profiles {:dev     {:repl-options {:init-ns taxis.server
                                      :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                       :env         {:is-dev true}}
             :uberjar {:hooks       [leiningen.cljsbuild]
                       :env         {:production true}
                       :main        taxis.server
                       :aot         :all}})


