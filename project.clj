(defproject timezone "1.0.0"

  :license {:url "http://www.eclipse.org/legal/epl-v10.html"
            :name "Eclipse Public License"}

  :description ""
  :url "https://github.com/llnek/timezone"

  :source-paths ["src/main/clojure" "src/main/cscript"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.671"]]

  :plugins [[lein-cljsbuild "1.1.6"]]

  :cljsbuild
  {:builds
   {:dev
    {:source-paths ["src/main/brepl" "src/main/cljsc"]
     :compiler {:output-to "public/js/main.js"
                :optimizations :whitespace
                :pretty-print true}}}}

  :clean-targets ^{:protect false} [:target-path "public/js/"])



