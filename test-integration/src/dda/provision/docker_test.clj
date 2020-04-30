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
   [dda.provision.docker :as sut2]))

(instrument)


(def testscript
  "
  pwd
  echo 'Hello'
  ls
  ")

(defn test-exec-script []
  (sut2/provide-container sut2/default-container :sut2/create-new-kill-esisting)
  (is (= true
         (sut/exec-script ::sut2/docker "testuser" testscript))))


(def failing-testscript
  "
  pwd
  ech 'Hello'
  ls
  ")

(defn test-exec-script-failing []
  (sut2/provide-container sut2/default-container :sut2/create-new-kill-esisting)
  (is (= false
         (sut/exec-script ::sut2/docker "testuser" failing-testscript))))



(defn test-exec-script-file []
  (sut2/provide-container sut2/default-container :sut2/create-new-kill-esisting)
  (is (= true
         (sut/exec-script-file ::sut2/docker "testuser" "modu" "should-copy" "aFile.sh"))))


(defn test-copy-resources-to-user-and-exec-as-user []
  (sut2/provide-container sut2/default-container :sut2/create-new-kill-esisting)
  (and
    (is (= true
          (sut/copy-resources-to-user ::sut2/docker "testuser" "modu" "should-copy" [{::sut/filename "aFile.sh"}])))
    (is (= true
          (sut/exec-as-user ::sut2/docker "testuser" "modu" "should-copy" "aFile.sh")))))


(defn test-copy-resources-to-tmp-and-exec-as-root []
  (sut2/provide-container sut2/default-container :sut2/create-new-kill-esisting)
  (and
    (is (= true
          (sut/copy-resources-to-tmp ::sut2/docker "modu" "should-copy" [{::sut/filename "aFile.sh"}])))
    (is (= true
          (sut/exec-as-root ::sut2/docker "modu" "should-copy" "aFile.sh")))))


(defn testAll []
  (and
    (test-exec-script)
    (test-exec-script-failing)
    (test-exec-script-file)
    (test-copy-resources-to-user-and-exec-as-user)
    (test-copy-resources-to-tmp-and-exec-as-root)))