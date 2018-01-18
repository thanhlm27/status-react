(ns status-im.ui.screens.views
  (:require-macros [status-im.utils.views :refer [defview letsubs] :as views])
  (:require [re-frame.core :refer [dispatch]]
            [status-im.utils.platform :refer [android?]]
            [status-im.ui.components.react :refer [view modal] :as react]
            [status-im.ui.components.styles :as common-styles]
            [status-im.ui.screens.main-tabs.views :refer [main-tabs]]
            [status-im.ui.components.context-menu :refer [menu-context]]

            [status-im.ui.screens.accounts.login.views :refer [login]]
            [status-im.ui.screens.accounts.recover.views :refer [recover recover-modal]]
            [status-im.ui.screens.accounts.views :refer [accounts]]

            [status-im.chat.screen :refer [chat]]
            [status-im.chat.new-chat.view :refer [new-chat]]
            [status-im.chat.new-public-chat.view :refer [new-public-chat]]

            [status-im.ui.screens.contacts.contact-list-modal.views :refer [contact-list-modal]]
            [status-im.ui.screens.contacts.new-contact.views :refer [new-contact]]

            [status-im.ui.screens.qr-scanner.views :refer [qr-scanner]]

            [status-im.ui.screens.group.views :refer [new-group edit-contact-group]]
            [status-im.ui.screens.group.chat-settings.views :refer [chat-group-settings]]
            [status-im.ui.screens.group.edit-contacts.views :refer [edit-contact-group-contact-list
                                                                    edit-chat-group-contact-list]]
            [status-im.ui.screens.group.add-contacts.views :refer [contact-toggle-list
                                                                   add-contacts-toggle-list
                                                                   add-participants-toggle-list]]
            [status-im.ui.screens.group.reorder.views :refer [reorder-groups]]

            [status-im.ui.screens.profile.views :as profile]
            [status-im.ui.screens.profile.photo-capture.views :refer [profile-photo-capture]]
            [status-im.ui.components.qr-code-viewer.views :as qr-code-viewer]

            [status-im.ui.screens.wallet.send.views :refer [send-transaction send-transaction-modal]]
            [status-im.ui.screens.wallet.choose-recipient.views :refer [choose-recipient]]
            [status-im.ui.screens.wallet.request.views :refer [request-transaction]]
            [status-im.ui.screens.wallet.components.views :as wallet.components]
            [status-im.ui.screens.wallet.send.views :as wallet.send]
            [status-im.ui.screens.wallet.settings.views :as wallet-settings]
            [status-im.ui.screens.wallet.transactions.views :as wallet-transactions]
            [status-im.ui.screens.wallet.send.transaction-sent.views :refer [transaction-sent transaction-sent-modal]]
            [status-im.ui.screens.wallet.components.views :refer [contact-code recent-recipients recipient-qr-code]]
            [status-im.ui.components.status-bar.view :as status-bar]
            [status-im.ui.screens.discover.search-results.views :as discover-search]
            [status-im.ui.screens.discover.recent-statuses.views :as discover-recent]
            [status-im.ui.screens.discover.all-dapps.views :as discover-all-dapps]
            [status-im.ui.screens.discover.popular-hashtags.views :as discover-popular]
            [status-im.ui.screens.discover.dapp-details.views :as discover-dapp-details]
            [status-im.ui.screens.network-settings.views :refer [network-settings]]
            [status-im.ui.screens.offline-messaging-settings.views :refer [offline-messaging-settings]]
            [status-im.ui.screens.network-settings.add-rpc.views :refer [add-rpc-url]]
            [status-im.ui.screens.network-settings.network-details.views :refer [network-details]]
            [status-im.ui.screens.network-settings.parse-json.views :refer [paste-json-text]]
            [status-im.ui.screens.browser.views :refer [browser]]))

(defn validate-current-view
  [current-view signed-up?]
  (if (or (contains? #{:login :chat :recover :accounts} current-view)
          signed-up?)
    current-view
    :chat))

;;; defines hierarchy of views, when parent screen is opened children screens
;;; are pre-rendered, currently it is:
;;;
;;; root-
;;;      |
;;;      - main-tabs -
;;;      |           |
;;;      - chat      |
;;;                  wallet
;;;                  - wallet-send-transaction -
;;;                  |                         |
;;;                  |                         - choose-recipient
;;;                  |                         |
;;;                  |                         - wallet-transaction-sent
;;;                  |
;;;                  - transactions-history, unsigned-transactions
;;;                  |
;;;                  - wallet-request-transaction -
;;;                  |                            |
;;;                  |                            - choose-recipient
;;;                  |
;;;                  my-profile
;;;                  - edit-my-profile -
;;;                                    |
;;;                                    - profile-photo-capture
(views/compile-views root-view
  [{:views     #{:home :wallet :my-profile}
    :component main-tabs}

   {:view      :chat
    :hide?     (not android?)
    :component chat}

   {:view      :wallet-send-transaction
    :parent    :wallet
    :component send-transaction}

   {:view      :wallet-request-transaction
    :parent    :wallet
    :component request-transaction}

   {:view      :wallet-request-assets
    :parent    :wallet-request-transaction
    :component wallet.components/request-assets}

   {:view      :choose-recipient
    :parent    :wallet-send-transaction
    :component choose-recipient}

   {:view      :wallet-transaction-sent
    :parent    :wallet-send-transaction
    :component transaction-sent}

   {:views     #{:transactions-history :unsigned-transactions}
    :parent    :wallet
    :component wallet-transactions/transactions}

   {:view      :profile-photo-capture
    :parent    :my-profile
    :component profile-photo-capture}])

(defview main []
  (letsubs [signed-up? [:signed-up?]
            view-id    [:get :view-id]
            modal-view [:get :modal]]
    {:component-will-update (fn [] (react/dismiss-keyboard!))}
    (when view-id
      (let [current-view (validate-current-view view-id signed-up?)]
        (let [component (case current-view
                          (:home :wallet :my-profile) main-tabs
                          :browser browser
                          :wallet-send-transaction send-transaction
                          :wallet-transaction-sent transaction-sent
                          :wallet-request-transaction request-transaction
                          (:transactions-history :unsigned-transactions) wallet-transactions/transactions
                          :wallet-transaction-details wallet-transactions/transaction-details
                          :wallet-send-assets wallet.components/send-assets
                          :wallet-request-assets wallet.components/request-assets
                          :new-chat new-chat
                          :new-group new-group
                          :edit-contact-group edit-contact-group
                          :chat-group-settings chat-group-settings
                          :add-contacts-toggle-list add-contacts-toggle-list
                          :add-participants-toggle-list add-participants-toggle-list
                          :edit-group-contact-list edit-contact-group-contact-list
                          :edit-chat-group-contact-list edit-chat-group-contact-list
                          :new-public-chat new-public-chat
                          :contact-toggle-list contact-toggle-list
                          :reorder-groups reorder-groups
                          :new-contact new-contact
                          :qr-scanner qr-scanner
                          :chat chat
                          :profile profile/profile
                          :discover-all-recent discover-recent/discover-all-recent
                          :discover-all-popular-hashtags discover-popular/discover-all-popular-hashtags
                          :discover-search-results discover-search/discover-search-results
                          :discover-dapp-details discover-dapp-details/dapp-details
                          :discover-all-dapps discover-all-dapps/main
                          :profile-photo-capture profile-photo-capture
                          :accounts accounts
                          :login login
                          :recover recover
                          :network-settings network-settings
                          :offline-messaging-settings offline-messaging-settings
                          :paste-json-text paste-json-text
                          :add-rpc-url add-rpc-url
                          :network-details network-details
                          :recent-recipients recent-recipients
                          :recipient-qr-code recipient-qr-code
                          :contact-code contact-code
                          :qr-viewer qr-code-viewer/qr-viewer
                          (throw (str "Unknown view: " current-view)))]
          [(if android? menu-context view) common-styles/flex
           [view common-styles/flex
            (if (and signed-up?
                     (#{:home :wallet :my-profile :chat :wallet-send-transaction
                        :choose-recipient :wallet-transaction-sent :transactions-history
                        :unsigned-transactions :wallet-request-transaction :edit-my-profile
                        :profile-photo-capture :wallet-request-assets}
                      current-view))
              [root-view]
              [component])
            (when modal-view
              [view common-styles/modal
               [modal {:animation-type   :slide
                       :transparent      true
                       :on-request-close #(dispatch [:navigate-back])}
                (let [component (case modal-view
                                  :qr-scanner qr-scanner
                                  :recover-modal recover-modal
                                  :contact-list-modal contact-list-modal
                                  :wallet-transactions-filter wallet-transactions/filter-history
                                  :wallet-settings-assets wallet-settings/manage-assets
                                  :wallet-send-transaction-modal send-transaction-modal
                                  :wallet-transaction-sent-modal transaction-sent-modal
                                  :wallet-transaction-fee wallet.send/transaction-fee
                                  (throw (str "Unknown modal view: " modal-view)))]
                  [component])]])]])))))
