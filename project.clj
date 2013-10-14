(defproject rain "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1934"]
                 [ring "1.2.0"]
                 [prismatic/dommy "0.1.1"]
                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]]
  :plugins [[lein-cljsbuild "0.3.4"]
            [lein-ring "0.8.7"]]
  :hooks [leiningen.cljsbuild]
  :source-paths ["src/clj"]
  :main rain.server
  :ring {:handler rain.server/app}

  :cljsbuild {:builds {:prod {:source-paths ["src/cljs"]
                              :jar true
                              :compiler {:output-to "resources/public/js/cljs.js"
                                         :source-map "resources/public/js/cljs.js.map"
                                         :optimizations :advanced
                                         :pretty-print false
                                         :externs ["resources/externs/stackblur.js"]}}
                       :dev {:source-paths ["src/cljs"]
                             :jar true
                             :compiler {:output-to "resources/public/js/cljs.dev.js"
                                        :optimizations :whitespace
                                        :pretty-print true
                                        :externs ["resources/externs/stackblur.js"]}}}})
