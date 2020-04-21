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
     [clojure.spec.alpha :as s]
     [clojure.spec.test.alpha :refer [instrument]]
     [orchestra.core :refer [defn-spec]]))

(s/def ::provisioner keyword?)
(s/def ::user string?)
(s/def ::module string?)
(s/def ::sub-module string?)
(s/def ::log-level #{::trace ::debug ::info ::warn ::error ::fatal})
(s/def ::log-message string?)

(s/def ::filename string?)
(s/def ::config map?)
(s/def ::file (s/keys :req [::filename] :opt[::config]))
(s/def ::files (s/coll-of ::file))

(defn-spec select-copy-resources-to-user keyword?
  [provisioner ::provisioner
   user ::user
   module ::module
   sub-module ::sub-module
   files ::files]
  provisioner)
(defmulti copy-resources-to-user select-copy-resources-to-user)

(defn-spec select-exec-as-user keyword?
  [provisioner ::provisioner
   user ::user
   module ::module
   sub-module ::sub-module
   filename ::filename]
  provisioner)
(defmulti exec-as-user select-exec-as-user)

(defn-spec select-copy-resources-to-tmp keyword?
  [provisioner ::provisioner
   module ::module
   sub-module ::sub-module
   files ::files]
  provisioner)
(defmulti copy-resources-to-tmp select-copy-resources-to-tmp)

(defn-spec select-exec-as-root keyword?
  [provisioner ::provisioner
   module ::module
   sub-module ::sub-module
   filename ::filename]
  provisioner)
(defmulti exec-as-root select-exec-as-root)

(defn-spec select-provision-log keyword?
  [provisioner ::provisioner
   module ::module
   log-level ::log-level
   log-mesage ::log-message]
  provisioner)
(defmulti provision-log select-provision-log)


(instrument `select-copy-resources-to-user)
(instrument `select-exec-as-user)
(instrument `select-copy-resources-to-tmp)
(instrument `select-exec-as-root)
(instrument `select-provision-log)
