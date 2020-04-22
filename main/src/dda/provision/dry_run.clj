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
(s/def ::copy (s/keys :req [::path ::mode ::owner ::group ::content]))
(s/def ::copies (s/coll-of ::copy))

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
  [user :dda.provision/user
   module-path string?
   sub-module :dda.provision/sub-module
   files :dda.provision/files]
  (let [base-path (str module-path "/" sub-module)]
    (map (fn [resource]
           (let [template? (contains? resource :dda.provision/config)
                 filename (:dda.provision/filename resource)
                 filename-on-target (str base-path "/" filename)
                 filename-on-source (if template?
                                      (str sub-module "/" filename ".template")
                                      (str sub-module "/" filename))
                 config (if template?
                          (:dda.provision/config resource)
                          {})
                 mode (cond
                        (contains? resource :dda.provision/mode) (:dda.provision/mode resource)
                        (string/ends-with? filename ".sh") "700"
                        :dda.provision/default "600")]
             {::path filename-on-target
              ::group user
              ::owner user
              ::mode mode
              ::content (selmer/render-file filename-on-source config)}))
         files)))

(s/fdef copy-resources-to-user
  :args (s/cat :provisioner :p/provisioner 
               :user :p/user 
               :module :p/module 
               :sub-module :p/sub-module 
               :files :p/files)
    :ret ::copies)
(defmethod p/copy-resources-to-user ::dry-run 
  [provisioner user module sub-module files]
  (copy-resources-to-path user (str "/home/" user "/resources/" module) sub-module files))

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