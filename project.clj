(defproject foghorn "1.0.0"

  :description ""
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.671"]]

  :profiles
  {:xxx {:dependencies [[com.cemerick/piggieback "0.2.2"]
                        [org.clojure/tools.nrepl "0.2.13"]]
         :repl-options
         {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}

  :plugins [[lein-cljsbuild "1.1.6"]]

  :source-paths ["src/main/clojure" "src/main/cljs"]

  :cljsbuild {:builds {:dev {:source-paths ["src/main/cljs"]
                             :compiler {:output-to "public/js/main.js"
                                        :optimizations :whitespace
                                        :pretty-print true}}}}

  :clean-targets ^{:protect false} [:target-path "public/js"])









