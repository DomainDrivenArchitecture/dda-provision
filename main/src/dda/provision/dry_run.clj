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
(ns dda.provision.dry-run
    (:require
     [clojure.string :as string]
     [clojure.spec.alpha :as s]
     [clojure.spec.test.alpha :refer [instrument]]
     [orchestra.core :refer [defn-spec]]
     [dda.provision :as p]
     [selmer.parser :as selmer]))

(s/def ::path string?)
(s/def ::mode string?)
(s/def ::owner string?)
(s/def ::group string?)
(s/def ::content string?)
(s/def ::copy (s/keys :req-un [::path ::mode ::owner ::group ::content]))
(s/def ::copies (s/coll-of ::copy))

(s/def ::filename string?)
(s/def ::execution-user string?)
(s/def ::execution-directory string?)
(s/def ::exec (s/keys :req-un [::execution-directory ::execution-user ::filename]))

(s/def ::exec-file-from-source any?)

(s/def ::log (s/keys :req-un [::p/module ::p/sub-module ::p/log-level ::p/log-message]))

(defn-spec dry-print string?
  [copies ::copies]
  (clojure.string/join
   "\n"
   (into
    ["copy files:"]
    (map str copies))))

(defn-spec
  ^{:private true}
  copy-resources-to-path ::copies
  [user ::p/user
   module-path string?
   sub-module ::p/sub-module
   files ::p/files]
  (let [base-path (str module-path "/" sub-module)]
    (map (fn [resource]
           (let [template? (contains? resource :config)
                 filename (:filename resource)
                 filename-on-target (str base-path "/" filename)
                 filename-on-source (if template?
                                      (str sub-module "/" filename ".template")
                                      (str sub-module "/" filename))
                 config (if template?
                          (:config resource)
                          {})
                 mode (cond
                        (contains? resource :mode) (:mode resource)
                        (string/ends-with? filename ".sh") "700"
                        :default "600")]
             {:path filename-on-target
              :group user
              :owner user
              :mode mode
              :content (selmer/render-file filename-on-source config)}))
         files)))

(defmethod p/copy-resources-to-user ::dry-run 
  [provisioner user module sub-module files]
  (copy-resources-to-path user (str "/home/" user "/resources/" module) sub-module files))
(s/fdef p/copy-resources-to-user
  :args (s/cat :provisioner ::p/provisioner
               :user ::p/user
               :module ::p/module
               :sub-module ::p/sub-module
               :files ::p/files)
  :ret ::copies)

(defmethod p/exec-as-user ::dry-run
  [provisioner user module sub-module filename]
  {:execution-directory
   (str "/home/" user "/resources/" module "/" sub-module)
   :execution-user user
   :filename filename})
(s/fdef p/exec-as-user
  :args (s/cat :provisioner ::p/provisioner
               :user ::p/user
               :module ::p/module
               :sub-module ::p/sub-module
               :filename ::p/filename)
  :ret ::exec)

(defmethod p/copy-resources-to-tmp ::dry-run
  [provisioner module sub-module files]
  (copy-resources-to-path "root" (str "/tmp/" module) sub-module files))
(s/fdef p/copy-resources-to-tmp
  :args (s/cat :provisioner ::p/provisioner
               :module ::p/module
               :sub-module ::p/sub-module
               :files ::p/files)
  :ret ::copies)

(defmethod p/exec-script ::dry-run
  [provisioner user content]
  {:execution-user user
   :content content})
(s/fdef p/exec-script-file
  :args (s/cat :provisioner ::p/provisioner
               :user ::p/user
               :content ::p/script-content)
  :ret ::exec)

(defmethod p/exec-script-file ::dry-run
  [provisioner user module sub-module filename]
  {:execution-directory
   (str "/home/" user "/resources/" module "/" sub-module)
   :execution-user user
   :filename filename})
(s/fdef p/exec-script-file
  :args (s/cat :provisioner ::p/provisioner
               :user ::p/user
               :module ::p/module
               :sub-module ::p/sub-module
               :filename ::p/filename)
  :ret ::exec)

(defmethod p/exec-as-root ::dry-run
  [provisioner module sub-module filename]
  {:execution-directory
   (str "/tmp/" module "/" sub-module)
   :execution-user "root"
   :filename filename})
(s/fdef p/exec-as-root
  :args (s/cat :provisioner ::p/provisioner
               :module ::p/module
               :sub-module ::p/sub-module
               :filename ::p/filename)
  :ret ::exec)

(defmethod p/provision-log ::dry-run
  [provisioner module sub-module log-level log-message]
  {:module module
   :sub-module sub-module
   :log-level log-level
   :log-message log-message})
(s/fdef p/provision-log
  :args (s/cat :provisioner ::p/provisioner
               :module ::p/module
               :sub-module ::p/log-level
               :log-level ::p/log-level
               :log-message ::p/log-message)
  :ret ::log)


(instrument `p/copy-resources-to-user)
(instrument `p/exec-as-user)
(instrument `p/exec-script-file)
(instrument `p/copy-resources-to-tmp)
(instrument `p/exec-as-root)
(instrument `p/provision-log)
