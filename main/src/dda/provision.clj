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
(ns dda.provision
    (:require
     [clojure.tools.logging :as logging]
     [clojure.spec.alpha :as s]
     [clojure.spec.test.alpha :refer [instrument]]
     [orchestra.core :refer [defn-spec]]
     [selmer.parser :as selmer]))

(s/def ::provisioner keyword?)
(s/def ::user string?)
(s/def ::module string?)

(s/def ::filename string?)
(s/def ::config map?)
(s/def ::file (s/keys :req [::filename] :opt[::config]))
(s/def ::files (s/coll-of ::file))

(defn-spec select-copy-resources-to-user keyword?
  [provisioner ::provisioner
   user ::user
   module ::module
   sub-module ::module
   files ::files]
  provisioner)
(defmulti copy-resources-to-user select-copy-resources-to-user)

(defn-spec select-exec-as-user keyword?
  [provisioner ::provisioner
   user ::user
   module ::module
   sub-module ::module
   filename ::filename]
  provisioner)
(defmulti exec-as-user select-exec-as-user)


(s/fdef copy-resources-to-user
  :args (s/cat :provisioner ::provisioner 
               :user ::user 
               :module ::module 
               :sub-module ::module 
               :files ::files))
(defmethod copy-resources-to-user ::dry-run 
  [provisioner user module sub-module files]
  (clojure.string/join
   "\n"
   (into 
    [(str "copies following files to /home/" user "/resource/" module "/" sub-module "/ :")]
    files)))

(s/fdef exec-as-user
  :args (s/cat :provisioner ::provisioner
               :user ::user
               :module ::module
               :sub-module ::module
               :filename ::filename))
(defmethod exec-as-user ::dry-run
  [provisioner user module sub-module filename]
  (str "execute /home/" user "/resource/" module "/" sub-module "/" filename))
    

(instrument `select-copy-resources-to-user)
(instrument `copy-resources-to-user)
(instrument `select-exec-as-user)
(instrument `exec-as-user)


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