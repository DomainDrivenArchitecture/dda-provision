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
     [clojure.spec.alpha :as s]
     [clojure.spec.test.alpha :refer [instrument]]
     [orchestra.core :refer [defn-spec]]
     [dda.provision :as p]
     [selmer.parser :as selmer]))

(s/def ::path string?)
(s/def ::mod string?)
(s/def ::owner string?)
(s/def ::group string?)
(s/def ::content string?)
(s/def ::copy (s/keys :req [::path ::mod ::owner ::group ::content]))
(s/def ::copies (s/coll-of ::copy))

(defn-spec dry-print string?
  [copies ::copies]
  (clojure.string/join
   "\n"
   (into
    ["copy files:"]
    (map str copies))))


(s/fdef copy-resources-to-user
  :args (s/cat :provisioner :p/provisioner 
               :user :p/user 
               :module :p/module 
               :sub-module :p/sub-module 
               :files :p/files)
    :ret ::copies)
(defmethod p/copy-resources-to-user ::dry-run 
  [provisioner user module sub-module files]
  [{:dda.provision.dry-run/path "/home/user/resource/module/sub-module/aFile"
    :dda.provision.dry-run/mod "644"
    :dda.provision.dry-run/owner "user"
    :dda.provision.dry-run/group "user"
    :dda.provision.dry-run/content "aFile"}])

(s/fdef exec-as-user
  :args (s/cat :provisioner :p/provisioner
               :user :p/user
               :module :p/module
               :sub-module :p/sub-module
               :filename :p/filename))
(defmethod p/exec-as-user ::dry-run
  [provisioner user module sub-module filename]
  (str "execute /home/" user "/resource/" module "/" sub-module "/" filename))
    

(instrument `p/select-exec-as-user)
(instrument `p/exec-as-user)


;; (s/defn copy-resources-to-tmp
;;   [facility :- s/Str
;;    module :- s/Str
;;    files :- [Resource]]
;;   (copy-resources-to-path "root" (tmp-path facility) module files))

;; (s/defn exec-as-root
;;   [facility :- s/Str
;;    module :- s/Str
;;    filename :-  s/Str]
;;   (let [facility-path (tmp-path (name facility))
;;         module-path (str facility-path "/" module)]
;;     (actions/exec-checked-script
;;      (str "execute " module "/" filename)
;;      ("cd" ~module-path)
;;      ("bash" ~filename))))


;; (s/defn log-info
;;   [facility :- s/Str
;;    log :- s/Str]
;;   (actions/as-action (logging/info (str facility " - " log))))