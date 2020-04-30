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
(ns dda.provision.remote
    (:require
     [clojure.string :as string]
     [clojure.spec.alpha :as s]
     [clojure.spec.test.alpha :refer [instrument]]
     [orchestra.core :refer [defn-spec]]
     [dda.provision :as p]
     [clojure.java.shell :as shell]
     [clojure.pprint :as pp])
    (:use [clj-ssh.cli])
    (:import (javax.swing JPasswordField
                          JOptionPane)))


(s/def ::path string?)
(s/def ::mode string?)
(s/def ::owner string?)
(s/def ::group string?)
(s/def ::content string?)
(s/def ::copy (s/keys :req [::path ::mode ::owner ::group ::content]))
(s/def ::copies (s/coll-of ::copy))

(s/def ::filename string?)
(s/def ::execution-user string?)
(s/def ::execution-directory string?)
(s/def ::exec (s/keys :req [::execution-directory ::execution-user ::filename]))

(s/def ::log (s/keys :req [::p/module ::p/sub-module ::p/log-level ::p/log-message]))


; ----------------- shell stuff
(defn escape-newline [text]
  (string/replace text "\n" "\\n"))


(defn format-result [result]
  (if (= 0 (:exit result))
    (str "SUCCESS - out: " (escape-newline (:out result)))
    (str "FAILED  - err: " (:err result))))


(defn sh-core
  "runs shell command locally, returning result vector"
  [command]
  (shell/sh "sh" "-c" command))


(defn split-script
  "splits a sh script in a set of sh command"
  [script]
  (filter (fn [x] (and (not= "" x) (not (string/starts-with? x "#"))))
          (map (fn [x] (string/trim x))
               (string/split-lines script))))


; ----------------- remote stuff
(def default-gopass-path "zwa/vm-pw")

(defn pw-by-prompt []
  (let [pf (JPasswordField.)]
    (do
      (JOptionPane/showConfirmDialog
        nil pf "Enter pw"  JOptionPane/OK_CANCEL_OPTION JOptionPane/PLAIN_MESSAGE)
      (clojure.string/join (.getPassword pf)))))

(defn pw-by-gopass [path]
  (let [res (sh-core (str "gopass " path))]
    (if (= 0 (:exit res))
        (:out res)
        (throw  (Exception. (str "password not found in " path))))))


(default-session-options {:strict-host-key-checking :no})

(def remote-user "az")
(def remote-pw (atom nil))
(def remote-host "192.168.56.122")

(defn get-remote-pw []
  (if (nil? remote-pw)
    (reset! remote-pw (pw-by-gopass default-gopass-path))
    @remote-pw))

; ----------------- remote
(defn remote-exec-command
  "executes a shell command cmd remotely"
  [cmd & [user]]
  (let [cmd-with-user (str (if (empty? user) "" (str "sudo -H -u " user " ")) cmd)]
    (pp/pprint (str "------------------------------- Remote CMD : " cmd-with-user " --------------------------------"))
    (let [result (ssh remote-host cmd-with-user :username remote-user :password (pw-by-gopass default-gopass-path))] ;todo use get-remote-pw
      (pp/pprint (format-result result))
      (= 0 (:exit result)))))


(defn exec-script-remote
  [script & [user]]
  (let [commands (split-script script)]
    (map (fn [command]
           (remote-exec-command command user))
      commands)))


; ******************************************
:todo
;(defn-spec
;  ^{:private true}
;  copy-resources-to-path ::copies
;  [user ::p/user
;   module-path string?
;   sub-module ::p/sub-module
;   files ::p/files]
;  (let [base-path (str module-path "/" sub-module)]
;    (map (fn [resource]
;           (let [template? (contains? resource ::p/config)
;                 filename (::p/filename resource)
;                 filename-on-target (str base-path "/" filename)
;                 filename-on-source (if template?
;                                      (str sub-module "/" filename ".template")
;                                      (str sub-module "/" filename))
;                 config (if template?
;                          (::p/config resource)
;                          {})
;                 mode (cond
;                        (contains? resource ::p/mode) (::p/mode resource)
;                        (string/ends-with? filename ".sh") "700"
;                        ::p/default "600")
;                 content (selmer/render-file filename-on-source config)]
;             (do
;               (remote-exec-command default-container (str "mkdir -p " base-path) user)
;               (docker-create-file-in-container default-container filename-on-target content user)
;               (docker-chmod-file default-container filename-on-target mode user)
;               (docker-chown-file default-container filename-on-target user user)
;               (docker-chgrp-file default-container filename-on-target user user))))
;         files)))
;
;(defmethod p/copy-resources-to-user ::remote
;  [provisioner user module sub-module files]
;  (copy-resources-to-path user (str "/home/" user "/resources/" module) sub-module files))
;(s/fdef p/copy-resources-to-user
;  :args (s/cat :provisioner ::p/provisioner
;               :user ::p/user
;               :module ::p/module
;               :sub-module ::p/sub-module
;               :files ::p/files)
;  :ret ::copies)
;
;
;(defmethod p/exec-as-user ::remote
;  [provisioner user module sub-module filename]
;  (let [execution-directory (str "/home/" user "/resources/" module "/" sub-module)]
;    (remote-exec-command default-container (str "cd " execution-directory " && ./" filename))))
;
;(s/fdef p/exec-as-user
;  :args (s/cat :provisioner ::p/provisioner
;               :user ::p/user
;               :module ::p/module
;               :sub-module ::p/sub-module
;               :filename ::p/filename)
;  :ret ::exec)
;
;
;(defmethod p/copy-resources-to-tmp ::remote
;  [provisioner module sub-module files]
;  (copy-resources-to-path "root" (str "/tmp/" module) sub-module files))
;
;(s/fdef p/copy-resources-to-tmp
;  :args (s/cat :provisioner ::p/provisioner
;               :module ::p/module
;               :sub-module ::p/sub-module
;               :files ::p/files)
;  :ret ::copies)
;
;
;(defmethod p/exec-as-root ::remote
;  [provisioner module sub-module filename]
;  (let [execution-directory (str "/tmp/" module "/" sub-module)]
;    (remote-exec-command default-container (str "cd " execution-directory " && ./" filename) "root")))
;
;
;(s/fdef p/exec-as-root
;  :args (s/cat :provisioner ::p/provisioner
;               :module ::p/module
;               :sub-module ::p/sub-module
;               :filename ::p/filename)
;  :ret ::exec)
;


(defmethod p/exec-script-file ::remote
  [provisioner user module sub-module filename]
  (let [file-with-path (str sub-module "/" filename)]
    (exec-script-remote (slurp (.getFile (clojure.java.io/resource file-with-path))) user)))

(s/fdef p/exec-script-file
        :args (s/cat :provisioner ::p/provisioner
                     :user ::p/user
                     :module ::p/module
                     :sub-module ::p/sub-module
                     :filename ::p/filename)
        :ret ::exec)


(defmethod p/provision-log ::remote
  [provisioner module sub-module log-level log-message]
  {::p/module module
   ::p/sub-module sub-module
   ::p/log-level log-level
   ::p/log-message log-message})
(s/fdef p/provision-log
  :args (s/cat :provisioner ::p/provisioner
               :module ::p/module
               :sub-module ::p/log-level
               :log-level ::p/log-level
               :log-message ::p/log-message)
  :ret ::log)


(instrument `p/copy-resources-to-user)
(instrument `p/exec-as-user)
(instrument `p/copy-resources-to-tmp)
(instrument `p/exec-as-root)
(instrument `p/provision-log)


; -------------------------------------
(defn demo []
  (do
    (dda.provision.remote/remote-exec-command "ls -al")
    (dda.provision/exec-script-file ::remote "az" "modu" "should-copy" "aFile.sh")))

;or in repl try e.g.:
;(dda.provision/exec-script ::remote "az" "mod" "vsc" "vsc-prereq.sh")