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
(ns dda.provision.dry-run-test
  (:require
   [clojure.test :refer :all]
   [clojure.spec.test.alpha :refer [instrument]]
   [data-test :refer :all]
   [dda.provision :as sut]
   [dda.provision.dry-run :as load-required]))

(instrument)

(defdatatest should-print-plain [input expected]
  (is (= expected
         (load-required/dry-print
          (sut/copy-resources-to-user (::sut/provisioner input)
                                      (::sut/user input)
                                      (::sut/module input)
                                      (::sut/sub-module input)
                                      (::sut/files input))))))

(defdatatest should-copy-to-user [input expected]
  (is (= expected
         (sut/copy-resources-to-user (::sut/provisioner input)
                                     (::sut/user input)
                                     (::sut/module input)
                                     (::sut/sub-module input)
                                     (::sut/files input)))))

(defdatatest should-execute-for-user [input expected]
  (is (= expected
         (sut/exec-as-user (::sut/provisioner input)
                           (::sut/user input)
                           (::sut/module input)
                           (::sut/sub-module input)
                           (::sut/filename input)))))

(defdatatest should-copy-to-tmp [input expected]
  (is (= expected
         (sut/copy-resources-to-tmp (::sut/provisioner input)
                                     (::sut/module input)
                                     (::sut/sub-module input)
                                     (::sut/files input)))))

(defdatatest should-execute-for-root [input expected]
  (is (= expected
         (sut/exec-as-root (::sut/provisioner input)
                           (::sut/module input)
                           (::sut/sub-module input)
                           (::sut/filename input)))))