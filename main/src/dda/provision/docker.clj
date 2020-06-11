; Licensed to the Apache Software Foundation (ASF) under one
; or more contributor license agreements. See the NOTICE file
; distributed with this work for additional information
; regarding copyright ownership. The ASF licenses this file
; to you under the Apache License, Version 2.0 (the
; "License"); you may not use this file except in compliance
; with the License. You may obtain a copy of the License at
;
; http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
(ns dda.provision.docker
    (:require
     [clojure.string :as string]
     [clojure.spec.alpha :as s]
     [clojure.spec.test.alpha :refer [instrument]]
     [orchestra.core :refer [defn-spec]]
     [selmer.parser :as selmer]
     [dda.provision :as p])
    (:use
      dda.provision.execution.docker))


; ************************* methods for dda-provision ::docker
(defn copy-resources-to-path
  [user
   module-path
   sub-module
   files]
  (let [base-path (str module-path "/" sub-module)]
    (reduce (fn [x y] (and x y))
      (map (fn [resource]
             (let [template? (contains? resource ::p/config)
                   filename (:filename resource)
                   filename-on-target (str base-path "/" filename)
                   filename-on-source (if template?
                                        (str sub-module "/" filename ".template")
                                        (str sub-module "/" filename))
                   config (if template?
                            (::p/config resource)
                            {})
                   mode (cond
                          (contains? resource ::p/mode) (::p/mode resource)
                          (string/ends-with? filename ".sh") "700"
                          ::p/default "600")
                   content (selmer/render-file filename-on-source config)]
               (do
                 (docker-exec default-container (str "mkdir -p " base-path) user)
                 (docker-create-file-in-container default-container filename-on-target content user)
                 (docker-chmod-file default-container filename-on-target mode user)
                 (docker-chown-file default-container filename-on-target user user)
                 (docker-chgrp-file default-container filename-on-target user user))))
           files))))


(defmethod p/copy-resources-to-user ::docker
  [provisioner user module sub-module files]
  (copy-resources-to-path user (str "/home/" user "/resources/" module) sub-module files))
(s/fdef p/copy-resources-to-user
  :args (s/cat :provisioner ::p/provisioner
               :user ::p/user
               :module ::p/module
               :sub-module ::p/sub-module
               :files ::p/files))


(defmethod p/exec-file-on-target-as-user ::docker
  [provisioner user module sub-module filename]
  (let [execution-directory (str "/home/" user "/resources/" module "/" sub-module)]
    (docker-exec default-container (str "cd " execution-directory " && ./" filename))))
(s/fdef p/exec-file-on-target-as-user
        :args (s/cat :provisioner ::p/provisioner
                     :user ::p/user
                     :module ::p/module
                     :sub-module ::p/sub-module
                     :filename ::p/filename))


(defmethod p/exec-command-as-user ::docker
  [provisioner user content]
  (provide-container default-container)
  (docker-exec-host-script default-container content user))
(s/fdef p/exec-command-as-user
        :args (s/cat :provisioner ::p/provisioner
                     :user ::p/user
                     :content ::p/command))


(defmethod p/exec-file-from-source-as-user ::docker
  [provisioner user module sub-module filename]
  (let [file-with-path (str sub-module "/" filename)]
    (provide-container default-container)
    (docker-exec-host-script default-container (slurp (.getFile (clojure.java.io/resource file-with-path))))))
(s/fdef p/exec-file-from-source-as-user
        :args (s/cat :provisioner ::p/provisioner
                     :user ::p/user
                     :module ::p/module
                     :sub-module ::p/sub-module
                     :filename ::p/filename))


(defmethod p/copy-resources-to-tmp ::docker
  [provisioner module sub-module files]
  (copy-resources-to-path "root" (str "/tmp/" module) sub-module files))
(s/fdef p/copy-resources-to-tmp
  :args (s/cat :provisioner ::p/provisioner
               :module ::p/module
               :sub-module ::p/sub-module
               :files ::p/files))


(defmethod p/exec-file-on-target-as-root ::docker
  [provisioner module sub-module filename]
  (let [execution-directory (str "/tmp/" module "/" sub-module)]
    (docker-exec default-container (str "cd " execution-directory " && ./" filename) "root")))
(s/fdef p/exec-file-on-target-as-root
        :args (s/cat :provisioner ::p/provisioner
                     :module ::p/module
                     :sub-module ::p/sub-module
                     :filename ::p/filename))

(defmethod p/exec-command-as-root ::docker
  [provisioner content]
  (provide-container default-container)
  (docker-exec-host-script default-container content "root"))
(s/fdef p/exec-command-as-root
  :args (s/cat :provisioner ::p/provisioner
               :content ::p/command))

(defmethod p/provision-log ::docker
  [provisioner module sub-module log-level log-message]
  {::p/module module
   ::p/sub-module sub-module
   ::p/log-level log-level
   ::p/log-message log-message})
(s/fdef p/provision-log
  :args (s/cat :provisioner ::p/provisioner
               :module ::p/module
               :sub-module ::p/log-level
               :log-level ::p/log-level
               :log-message ::p/log-message))


(instrument `p/copy-resources-to-user)
(instrument `p/copy-resources-to-tmp)
(instrument `p/exec-file-on-target-as-user)
(instrument `p/exec-command-as-user)
(instrument `p/exec-file-on-target-as-root)
(instrument `p/exec-command-as-root)
(instrument `p/exec-file-from-source-as-user)
(instrument `p/provision-log)
