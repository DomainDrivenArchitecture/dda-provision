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
(ns dda.provision.docker-test
  (:require
   [clojure.test :refer :all]
   [clojure.spec.test.alpha :refer [instrument]]
   [data-test :refer :all]
   [dda.provision :as sut]
   [dda.provision.docker :as sut2]
   [dda.provision.execution.docker :as sut3]))

(instrument)


(def testscript
  "
  pwd
  echo 'Hello'
  ls
  ")

(def failing-testscript
  "
  pwd
  ech 'Hello'
  ls
  ")


(defn test-exec-command-as-user []
  (sut3/provide-container sut3/default-container :sut2/create-new-kill-existing)
  (is (= nil
         (sut/exec-command-as-user ::sut2/docker "testuser" testscript))))


(defn test-exec-command-as-root []
  (sut3/provide-container sut3/default-container :sut2/create-new-kill-existing)
  (is (= nil
         (sut/exec-command-as-root ::sut2/docker testscript))))


(defn test-copy-resources-to-user-and-exec-as-user []
  (sut3/provide-container sut3/default-container :sut2/create-new-kill-eisting)
  (and
    (is (= nil
           (sut/copy-resources-to-user ::sut2/docker "testuser" "modu" "should-copy" [{:filename "aFile.sh"}])))
    (is (= nil
           (sut/exec-file-on-target-as-user ::sut2/docker "testuser" "modu" "should-copy" "aFile.sh")))))


(defn test-copy-resources-to-tmp-and-exec-as-root []
  (sut3/provide-container sut3/default-container :sut2/create-new-kill-existing)
  (and
    (is (= nil
           (sut/copy-resources-to-tmp ::sut2/docker "modu" "should-copy" [{:filename "aFile.sh"}])))
    (is (= nil
           (sut/exec-file-on-target-as-root ::sut2/docker "modu" "should-copy" "aFile.sh")))))


(defn test-exec-command-as-user-failing []
  (sut3/provide-container sut3/default-container :sut2/create-new-kill-existing)
  (try
    (sut/exec-command-as-user ::sut2/docker "testuser" failing-testscript)
    (catch Exception e true)))


(defn test-exec-file-from-source-as-user []
  (sut3/provide-container sut3/default-container :sut2/create-new-kill-existing)
  (is (= nil
         (sut/exec-file-from-source-as-user ::sut2/docker "testuser" "modu" "should-copy" "aFile.sh"))))


(defn test-exec-file-from-source-as-root []
  (sut3/provide-container sut3/default-container :sut2/create-new-kill-existing)
  (is (= nil
         (sut/exec-file-from-source-as-root ::sut2/docker "modu" "should-copy" "aFile.sh"))))


(defn testAll []
  (and
    (test-exec-command-as-user)
    (test-exec-command-as-root)
    (test-exec-command-as-user-failing)
    (test-exec-file-from-source-as-user)
    (test-exec-file-from-source-as-root)
    (test-copy-resources-to-user-and-exec-as-user)
    (test-copy-resources-to-tmp-and-exec-as-root)))