(ns status-im.chat.models.message
  (:require [re-frame.core :as re-frame]
            [status-im.utils.clocks :as clocks]
            [status-im.constants :as constants]
            [status-im.chat.models :as chat-model]
            [status-im.chat.models.commands :as commands-model]
            [status-im.chat.events.requests :as requests-events]
            [taoensso.timbre :as log]))

(defn- get-current-account
  [{:accounts/keys [accounts current-account-id]}]
  (get accounts current-account-id))

(def receive-interceptors
  [(re-frame/inject-cofx :random-id) (re-frame/inject-cofx :get-stored-message) re-frame/trim-v])

(defn- lookup-response-ref
  [access-scope->commands-responses account chat contacts response-name]
  (let [available-commands-responses (commands-model/commands-responses :response
                                                                        access-scope->commands-responses
                                                                        account
                                                                        chat
                                                                        contacts)]
    (:ref (get available-commands-responses response-name))))

(defn add-message-to-db
  [db chat-id {:keys [message-id clock-value] :as message} current-chat?]
  (let [prepared-message (cond-> (assoc message
                                        :chat-id    chat-id
                                        :appearing? true)
                           (not current-chat?)
                           (assoc :appearing? false))]
    (cond-> (-> db
                (update-in [:chats chat-id :messages] assoc message-id prepared-message)
                (update-in [:chats chat-id :last-clock-value] (fnil max 0) clock-value))
      (not current-chat?)
      (update-in [:chats chat-id :unviewed-messages] (fnil conj #{}) message-id))))

(defn receive
  [{:keys [db now] :as cofx}
   {:keys [from group-id chat-id content-type content message-id timestamp clock-value]
    :as   message}]
  (let [{:keys [current-chat-id view-id
                access-scope->commands-responses] :contacts/keys [contacts]} db
        {:keys [public-key] :as current-account} (get-current-account db)
        chat-identifier (or group-id chat-id from)]
    ;; proceed with adding message if message is not already stored in realm,
    ;; it's not from current user (outgoing message) and it's for relevant chat
    ;; (either current active chat or new chat not existing yet or it's a direct message)
    (let [current-chat?    (and (= :chat view-id)
                                (= current-chat-id chat-identifier))
          fx               (if (get-in db [:chats chat-identifier])
                             (chat-model/upsert-chat cofx {:chat-id chat-identifier
                                                           :group-chat (boolean group-id)})
                             (chat-model/add-chat cofx chat-identifier))
          chat             (get-in fx [:db :chats chat-identifier])
          command-request? (= content-type constants/content-type-command-request)
          command          (:command content)
          enriched-message (cond-> (assoc message
                                          :chat-id     chat-identifier
                                          :timestamp   (or timestamp now)
                                          :show?       true
                                          :clock-value (clocks/receive clock-value (:last-clock-value chat)))
                             public-key
                             (assoc :user-statuses {public-key (if current-chat? :seen :received)})
                             (and command command-request?)
                             (assoc-in [:content :content-command-ref]
                                       (lookup-response-ref access-scope->commands-responses
                                                            current-account chat contacts command)))]
      (cond-> (-> fx
                  (update :db add-message-to-db chat-identifier enriched-message current-chat?)
                  (assoc :save-message enriched-message))
        command-request?
        (requests-events/add-request chat-identifier enriched-message)))))

(defn add-to-chat?
  [{:keys [db get-stored-message]} {:keys [group-id chat-id from message-id]}]
  (let [chat-identifier                                  (or group-id chat-id from)
        {:keys [chats deleted-chats current-public-key]} db 
        {:keys [messages not-loaded-message-ids]}        (get chats chat-identifier)]
    (when (not= from current-public-key)
      (if group-id
        (not (or (get deleted-chats chat-identifier)
                 (get messages message-id)
                 (get not-loaded-message-ids message-id)))
        (not (or (get messages message-id)
                 (get not-loaded-message-ids message-id)
                 (and (get deleted-chats chat-identifier)
                      (get-stored-message message-id))))))))

(defn message-seen-by? [message user-pk]
  (= :seen (get-in message [:user-statuses user-pk])))
