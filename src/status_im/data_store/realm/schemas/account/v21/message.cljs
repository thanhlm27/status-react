(ns status-im.data-store.realm.schemas.account.v21.message
  (:require [taoensso.timbre :as log]
            [clojure.string :as str]))

(def schema {:name       :message
             :primaryKey :message-id
             :properties {:message-id     :string
                          :from           :string
                          :to             {:type     :string
                                           :optional true}
                          :group-id       {:type     :string
                                           :optional true}
                          :content        :string
                          :content-type   :string
                          :username       {:type     :string
                                           :optional true}
                          :timestamp      :int
                          :chat-id        {:type    :string
                                           :indexed true}
                          :outgoing       :bool
                          :retry-count    {:type    :int
                                           :default 0}
                          :message-type   {:type     :string
                                           :optional true}
                          :message-status {:type     :string
                                           :optional true}
                          :user-statuses  {:type       :list
                                           :objectType :user-status}
                          :clock-value    {:type    :int
                                           :default 0}
                          :show?          {:type    :bool
                                           :default true}}})

(defn migration [old-realm new-realm]
  (log/debug "migrating message schema v21")
  (let [messages (.objects new-realm "message")]
    (dotimes [i (.-length messages)]
      (let [message (aget messages i)
            content (aget message "content")
            type    (aget message "content-type")]
        (when (and (= type "command")
                   (> (str/index-of content "command=location") -1))
          (aset message "show?" false))))))

