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
(ns dda.provision.execution.local
  (:require
    [clojure.string :as string]
    ;[clojure.spec.alpha :as s]
    [clojure.tools.logging :as log]
    ;[orchestra.core :refer [defn-spec]]
    [clojure.java.shell :as shell]))


(defn escape-double-quote [text]
  (string/replace text "\"" "\\\""))


(defn escape-single-quote [text]
  (string/replace text "'" "\\'"))


(defn escape-newline [text]
  (string/replace text "\n" "\\n"))


(defn format-result [result]
  (if (= 0 (:exit result))
    (str "SUCCESS" (if (not (empty? (string/trim (:out result)))) (str "  - out: " (escape-newline (:out result))) ""))
    (str "FAILED  - err: " (:err result))))


(defn sh-result-nolog
  "runs a shell command locally, returning result map with :exit :out and :err, not logging the result"
  [command]
  (shell/sh "sh" "-c" command))


(defn sh-result
  "runs a shell command locally, returning result map with :exit :out and :err"
  [command]
  (do
    (log/info (str "-------------------- CMD : " (escape-newline command) " -------------------"))
    (let [result (shell/sh "sh" "-c" command)]
      (log/info (format-result result))
      result)))


(defn sh
  "runs a shell command locally, returning true or false in case of success resp. failure"
  [command]
  (= 0 (:exit (sh-result command))))


(defn split-script
  "splits a shell script in a list of shell commands"
  [script]
  (filter (fn [x] (and (not= "" x) (not (string/starts-with? x "#"))))
          (map (fn [x] (string/trim x))
               (string/split-lines script))))


(defn sh-multi-lines [script]
  "executes a list of shell commands, returns true if all completed successfully"
  (if (not (empty? script))
    (and
      (sh (first script))
      (sh-multi-lines (rest script)))
    true))


(defn sh-script
  "executes a multi-line script locally"
  [script]
  (sh-multi-lines (split-script script)))

