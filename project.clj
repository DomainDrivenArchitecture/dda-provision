(defproject dda/dda-provision "0.1.1-SNAPSHOT"
  :description "tools for provisioning"
  :url "https://www.domaindrivenarchitecture.org"
  :license {:name "Apache License, Version 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [orchestra "2018.12.06-2"]
                 [selmer "1.12.23"]
                 [dda/dda-config-commons "1.5.0"]
                 [clj-commons/clj-ssh "0.5.15"]]
  :source-paths ["main/src"]
  :resource-paths ["main/resources"]
  :repositories [["snapshots" :clojars]
                 ["releases" :clojars]]
  :deploy-repositories [["snapshots" :clojars]
                        ["releases" :clojars]]
  :profiles {:dev {:source-paths ["test/src"
                                  "test-integration/src"
                                  "uberjar/src"]
                   :resource-paths ["test/resources"]
                   :dependencies
                   [[dda/data-test "0.1.1"]]
                   :leiningen/reply
                   {:dependencies [[org.slf4j/jcl-over-slf4j "1.8.0-beta0"]]
                    :exclusions [commons-logging]}}
             :test {:test-paths ["test/src"]
                    :resource-paths ["test/resources"]
                    :dependencies [[dda/data-test "0.1.1"]]}}
  :local-repo-classpath true)
