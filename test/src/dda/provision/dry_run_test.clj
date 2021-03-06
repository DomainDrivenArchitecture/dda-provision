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
          (sut/copy-resources-to-user (:provisioner input)
                                      (:user input)
                                      (:module input)
                                      (:sub-module input)
                                      (:files input))))))

(defdatatest should-copy-to-user [input expected]
  (is (= expected
         (sut/copy-resources-to-user (:provisioner input)
                                     (:user input)
                                     (:module input)
                                     (:sub-module input)
                                     (:files input)))))

(defdatatest should-execute-for-user [input expected]
  (is (= expected
         (sut/exec-file-on-target-as-user (:provisioner input)
                                          (:user input)
                                          (:module input)
                                          (:sub-module input)
                                          (:filename input)))))

(defdatatest should-exec-command-as-user [input expected]
             (is (= expected
                    (sut/exec-command-as-user (:provisioner input)
                                              (:user input)
                                              (:command input)))))

(defdatatest should-exec-command-as-root [input expected]
             (is (= expected
                    (sut/exec-command-as-root (:provisioner input)
                                              (:command input)))))

(defdatatest should-exec-file-from-source-as-user [input expected]
  (is (= expected
         (sut/exec-file-from-source-as-user (:provisioner input)
                                            (:user input)
                                            (:module input)
                                            (:sub-module input)
                                            (:filename input)))))

(defdatatest should-copy-to-tmp [input expected]
  (is (= expected
         (sut/copy-resources-to-tmp (:provisioner input)
                                    (:module input)
                                    (:sub-module input)
                                    (:files input)))))

(defdatatest should-execute-for-root [input expected]
  (is (= expected
         (sut/exec-file-on-target-as-root (:provisioner input)
                                          (:module input)
                                          (:sub-module input)
                                          (:filename input)))))
