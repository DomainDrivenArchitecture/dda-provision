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
     [orchestra.core :refer [defn-spec]]
     [dda.provision :as p]
     [selmer.parser :as selmer]
     [clojure.java.shell :as shell]
     [clojure.pprint :as pp]))


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
(defn formatResult [result]
  (if (= 0 (:exit result))
    (str "SUCCESS - out: " (:out result))
    (str "FAILED  - err: " (:err result))))


(defn sh
  "runs shell command cmd locally, returning true or false for success resp. failure"
  [cmd]
  (do
    (pp/pprint (str "------------------------------- CMD : " cmd " --------------------------------"))
    (let [result (shell/sh "sh" "-c" cmd)]
      (pp/pprint (formatResult result))
      (= 0 (:exit result)))))


(defn splitShScript
  "splits a sh script in a set of sh command"
  [script]
  (filter (fn [x] (and (not= "" x) (not (string/starts-with? x "#"))))
          (map (fn [x] (string/trim x))
               (string/split-lines script))))


(defn shMLRec [script]
  (if (not (empty? script))
    (and
      (sh (first script))
      (shMLRec (rest script)))
    true))


(defn shScript
  "executes a multi-line script locally"
  [script]
  (shMLRec (splitShScript script)))


; ----------------- docker stuff
(def dockerImage "ubuntu_plus_user")
(def defaultContainer "cljdock")


(defn isRunning [container]
  (= "true\n" (:out (sh (str "sudo docker inspect -f '{{.State.Running}}' " container)))))


(defn runCont [container & [image]]
  (let [image (or image "ubuntu")]
    (sh (str "sudo docker run -dit --name " container " " image))))


(defn exitAndRmCont [container]
  (do
    (sh (str "sudo docker stop " container))
    (sh (str "sudo docker rm -f " container))))


(defn provideContainer [container & [image option]]
  (let [container (or container defaultContainer)
        image (or dockerImage)
        option (or option :use-existing-else-create)]
    (if (= option :create-new-kill-existing)
      (do
        (exitAndRmCont container)
        (runCont container image))
      (if (= option :use-existing-else-create)
        (if (not (isRunning container))
          (do
            (exitAndRmCont container)
            (runCont container image)))))))


(defn dockerExec
  "executes a shell command cmd in a running docker container"
  [container cmd]
  (sh (str "sudo docker exec " container " sh -c \"" (string/replace (string/replace cmd "\\" "\\\\") "\"" "\\\"") "\"")))


(defn dockerCopyFileToContainer
  "executes a shell command cmd in a running docker container"
  [src-file container destination-path]
  (sh (str "sudo docker cp " src-file " " container ":" destination-path)))



; ******************************************
(defn-spec dry-print string?
  [copies ::copies]
  (clojure.string/join
   "\n"
   (into
    ["copy files:"]
    (map str copies))))

(defn-spec
  ^{:private true}
  copy-resources-to-path ::copies
  [user ::p/user
   module-path string?
   sub-module ::p/sub-module
   files ::p/files]
  (let [base-path (str module-path "/" sub-module)]
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
                        ::p/default "600")]
             {::path filename-on-target
              ::group user
              ::owner user
              ::mode mode
              ::content (selmer/render-file filename-on-source config)}
             (pp/pprint (str " xxxxxxxxxxxxxxxxxxx" user))))
         files)))

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
  {::execution-directory
   (str "/home/" user "/resources/" module "/" sub-module)
   ::execution-user user
   ::filename filename})
(s/fdef p/exec-as-user
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
  {::execution-directory
   (str "/tmp/" module "/" sub-module)
   ::execution-user "root"
   ::filename filename})
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
(instrument `p/copy-resources-to-tmp)
(instrument `p/exec-as-root)
(instrument `p/provision-log)


;; (s/defn log-info
;;   [facility :- s/Str
;;    log :- s/Str]
;;   (actions/as-action (logging/info (str facility " - " log))))

(defn a [] pp/pprint "ggggggggggggggggggggggggggggggggggggggg")