(ns status-im.ui.components.action-sheet
  (:require [status-im.i18n :as i18n]
            [status-im.utils.utils :as utils]
            [status-im.react-native.js-dependencies :as rn-dependencies]))

(defn- callback [options]
  (fn [index]
    (when (< index (count options))
      (when-let [handler (:action (nth options index))]
        (handler)))))

(defn- options [title message options]
  (let [destructive-opt-index (utils/first-index :destructive? options)] ;; TODO Can only be a single destructive?
    (clj->js (merge {:options           (conj (mapv :label options) (i18n/label :t/cancel))
                     :cancelButtonIndex (count options)}
                    (when destructive-opt-index
                      {:destructiveButtonIndex destructive-opt-index})
                    (when title {:title title})
                    (when message {:message message})))))

(defn show [{:keys [title message options]}]
  (.showActionSheetWithOptions (.-ActionSheetIOS rn-dependencies/react-native)
                               (options title message options)
                               (callback options)))
