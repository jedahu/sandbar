(defproject me.panzoo/sandbar "0.4.0-SNAPSHOT"
  :description "Clojure web application libraries built on top of Ring
                and Compojure."
  :url "http://github.com/brentonashworth/sandbar"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [ring/ring-core "0.3.7"]
                 [compojure "0.6.2"]
                 [hiccup "0.3.4"]
                 [inflections "0.4.2-SNAPSHOT"]]
  :dev-dependencies [[jline "0.9.94"]
                     [ring/ring-devel "0.3.7"]
                     [ring/ring-jetty-adapter "0.3.7"]
                     [ring/ring-httpcore-adapter "0.3.5"]
                     [lein-difftest "1.3.2-SNAPSHOT"]
                     [radagast "1.0.0"]
                     [enlive "1.0.0"]
                     [marginalia "0.5.0"]]
  :hooks [leiningen.hooks.difftest]
  :radagast/ns-whitelist #"^sandbar.*")
