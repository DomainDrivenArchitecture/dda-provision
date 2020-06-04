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
(ns dda.provision.execution.docker
  (:require
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [clojure.java.shell :as shell]
    [clojure.pprint :as pp]))


(def default-shell "sh")
(def default-image "ubuntu_with_user")
(def default-container "dda-provision-tests")


; ----------------- local shell support
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


(defn sh-result
  "runs shell command locally, returning result vector"
  [command]
  (do
    (log/info (str "-------------------- CMD : " (escape-newline command) " -------------------"))
    (let [result (shell/sh "sh" "-c" command)]
      (log/info (format-result result))
      result)))


(defn sh
  "runs shell command locally, throws exception in case of failure"
  [command]
  (let [result (sh-result command)]
    (if (not= 0 (:exit result))
      (throw (Exception. (str "CMD FAILED (" command ") -- (REASON: " (:err result) ")"))))))


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


(defn build-image [name path & [option]]
  (if (or (not (image-exists? name)) (= option :create-new-kill-existing))
    (sh (str "cd " path " && sudo docker build --tag " name " ."))))


(defn provide-image [image & [option]]
  (if (or (not @image-initialized?) (= option :create-new-kill-existing))
    (let [path (str "main/resources/docker-images/" image)]
      (if (or (not (image-exists? image)) (= option :create-new-kill-existing))
        (build-image image path option))
      (reset! image-initialized? true))))


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

