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
(ns dda.provision.docker
    (:require
     [clojure.string :as string]
     [clojure.spec.alpha :as s]
     [clojure.spec.test.alpha :refer [instrument]]
     [clojure.tools.logging :as log]
     [clojure.java.shell :as shell]
     [orchestra.core :refer [defn-spec]]
     [dda.provision :as p]
     [selmer.parser :as selmer]))



; ------------------ docker settings
(def default-container "dda-provision-tests")
(def default-shell "sh")
(def default-image "ubuntu_with_user")


; ---------------- types
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


; ----------------- local shell support
(defn escape-double-quote [text]
  (string/replace text "\"" "\\\""))


(defn escape-single-quote [text]
  (string/replace text "'" "\\'"))


(defn escape-newline [text]
  (string/replace text "\n" "\\n"))


(defn format-result [result]
  (if (= 0 (:exit result))
    (str "SUCCESS - out: " (escape-newline (:out result)))
    (str "FAILED  - err: " (:err result))))


(defn sh-result
  "runs shell command locally, returning result vector"
  [command]
  (do
    (log/info (str "-------------------- CMD : " (escape-newline command) " -------------------"))
    (let [result (shell/sh "sh" "-c" command)]
      (log/info (format-result result))
      result)))


(defn sh
  "runs shell command locally, returning true or false for success resp. failure"
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


; ----------------- docker support
(def image-initialized? (atom false))


(defn image-exists? [imageName]
  (not= "" (:out (sh-result (str "sudo docker images " imageName " -q")))))


(defn build-image [name path]
  (if (not (image-exists? name))
    (sh (str "cd " path " && sudo docker build --tag " name " ."))))


(defn provide-image [image]
  (if (not @image-initialized?)
    (let [path (str "main/resources/docker-images/" image)]
      (if (not (image-exists? image))
        (build-image image path))
      (swap! image-initialized? not))))


(defn running? [container]
  (= "true\n" (:out (sh-result (str "sudo docker inspect -f '{{.State.Running}}' " container)))))


(defn run-container [container & [image]]
  (let [image (or image "ubuntu")]
    (sh (str "sudo docker run -dit --name " container " " image))))


(defn stop-and-rm-container [container]
  (do
    (sh (str "sudo docker stop " container))
    (sh (str "sudo docker rm -f " container))))


(defn provide-container [container & [image option]]
  (let [container (or container default-container)
        image (or default-image)
        option (or option :use-existing-else-create)]
    (provide-image image)
    (if (= option :create-new-kill-existing)
      (do
        (stop-and-rm-container container)
        (run-container container image))
      (if (= option :use-existing-else-create)
        (if (not (running? container))
          (do
            (stop-and-rm-container container)
            (run-container container image)))))))


(defn docker-exec
  "executes a shell command cmd in a running docker container"
  [container cmd & [user]]
  (let [with-user (if (nil? user) "" (str "sudo -H -u " user))]
    (sh (str "sudo docker exec " container " " with-user " " default-shell " -c \"" (escape-double-quote cmd) "\""))))


(defn docker-exec-host-script
  [container script & [user]]
  (let [commands (split-script script)]
    (reduce (fn [x y] (and x y))
      (map (fn [command]
             (docker-exec container command user))
        commands))))


(defn docker-copy-file-to-container
  [src-file container destination-path]
  (sh (str "sudo docker cp " src-file " " container ":" destination-path)))


(defn docker-create-file-in-container
  [container file text & [user]]
  (docker-exec container (str "printf \"" text "\" > " file) user))


(defn docker-chmod-file
  [container file mode & [user]]
  (docker-exec container (str "chmod " mode " " file) user))


(defn docker-chown-file
  [container file owner & [user]]
  (docker-exec container (str "chown " owner " " file) user))


(defn docker-chgrp-file
  [container file group & [user]]
  (docker-exec container (str "chgrp " group " " file) user))


; ************************* methods for dda-provision ::docker
(defn-spec
  ^{:private true}
  copy-resources-to-path ::copies
  [user ::p/user
   module-path string?
   sub-module ::p/sub-module
   files ::p/files]
  (let [base-path (str module-path "/" sub-module)]
    (reduce (fn [x y] (and x y))
      (map (fn [resource]
             (let [template? (contains? resource ::p/config)
                   filename (::p/filename resource)
                   filename-on-target (str base-path "/" filename)
                   filename-on-source (if template?
                                        (str sub-module "/" filename ".template")
                                        (str sub-module "/" filename))
                   config (if template?
                            (::p/config resource)
                            {})
                   mode (cond
                          (contains? resource ::p/mode) (::p/mode resource)
                          (string/ends-with? filename ".sh") "700"
                          ::p/default "600")
                   content (selmer/render-file filename-on-source config)]
               (do
                 (docker-exec default-container (str "mkdir -p " base-path) user)
                 (docker-create-file-in-container default-container filename-on-target content user)
                 (docker-chmod-file default-container filename-on-target mode user)
                 (docker-chown-file default-container filename-on-target user user)
                 (docker-chgrp-file default-container filename-on-target user user))))
           files))))


(defmethod p/copy-resources-to-user ::docker
  [provisioner user module sub-module files]
  (copy-resources-to-path user (str "/home/" user "/resources/" module) sub-module files))
(s/fdef p/copy-resources-to-user
  :args (s/cat :provisioner ::p/provisioner
               :user ::p/user
               :module ::p/module
               :sub-module ::p/sub-module
               :files ::p/files)
  :ret ::copies)


(defmethod p/exec-as-user ::docker
  [provisioner user module sub-module filename]
  (let [execution-directory (str "/home/" user "/resources/" module "/" sub-module)]
    (docker-exec default-container (str "cd " execution-directory " && ./" filename))))
(s/fdef p/exec-as-user
  :args (s/cat :provisioner ::p/provisioner
               :user ::p/user
               :module ::p/module
               :sub-module ::p/sub-module
               :filename ::p/filename)
  :ret ::exec)


(defmethod p/exec-script ::docker
  [provisioner user content]
  (provide-container default-container)
  (docker-exec-host-script default-container content user))
(s/fdef p/exec-script
        :args (s/cat :provisioner ::p/provisioner
                     :user ::p/user
                     :content ::p/script-content)
        :ret ::exec)


(defmethod p/exec-script-file ::docker
  [provisioner user module sub-module filename]
  (let [file-with-path (str sub-module "/" filename)]
    (provide-container default-container)
    (docker-exec-host-script default-container (slurp (.getFile (clojure.java.io/resource file-with-path))))))
(s/fdef p/exec-script-file
        :args (s/cat :provisioner ::p/provisioner
                     :user ::p/user
                     :module ::p/module
                     :sub-module ::p/sub-module
                     :filename ::p/filename)
        :ret ::exec)


(defmethod p/copy-resources-to-tmp ::docker
  [provisioner module sub-module files]
  (copy-resources-to-path "root" (str "/tmp/" module) sub-module files))
(s/fdef p/copy-resources-to-tmp
  :args (s/cat :provisioner ::p/provisioner
               :module ::p/module
               :sub-module ::p/sub-module
               :files ::p/files)
  :ret ::copies)


(defmethod p/exec-as-root ::docker
  [provisioner module sub-module filename]
  (let [execution-directory (str "/tmp/" module "/" sub-module)]
    (docker-exec default-container (str "cd " execution-directory " && ./" filename) "root")))
(s/fdef p/exec-as-root
  :args (s/cat :provisioner ::p/provisioner
               :module ::p/module
               :sub-module ::p/sub-module
               :filename ::p/filename)
  :ret ::exec)


(defmethod p/provision-log ::docker
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
(instrument `p/exec-script-file)
(instrument `p/copy-resources-to-tmp)
(instrument `p/exec-as-root)
(instrument `p/provision-log)
