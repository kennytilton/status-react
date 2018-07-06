(ns status-im.ui.screens.browser.views
  (:require-macros [status-im.utils.slurp :refer [slurp]]
                   [status-im.utils.views :as views])
  (:require [cljs.reader :as reader]
            [re-frame.core :as re-frame]
            [status-im.ui.components.react :as react]
            [status-im.ui.screens.browser.styles :as styles]
            [status-im.ui.components.status-bar.view :as status-bar]
            [status-im.ui.components.toolbar.view :as toolbar.view]
            [status-im.ui.components.webview-bridge :as components.webview-bridge]
            [status-im.utils.js-resources :as js-res]
            [status-im.ui.components.react :as components]
            [reagent.core :as reagent]
            [status-im.ui.components.chat-icon.screen :as chat-icon.screen]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.i18n :as i18n]
            [status-im.utils.ethereum.core :as ethereum]
            [status-im.ui.components.toolbar.actions :as actions]
            [status-im.ui.components.tooltip.views :as tooltip]))

(views/defview toolbar-content-dapp [name]
  (views/letsubs [dapp [:get-dapp-by-name name]]
    [react/view
     [react/view styles/toolbar-content-dapp
      [chat-icon.screen/dapp-icon-browser dapp 36]
      [react/view styles/dapp-name
       [react/text {:style               styles/dapp-name-text
                    :number-of-lines     1
                    :font                :toolbar-title
                    :accessibility-label :dapp-name-text}
        name]
       [react/text {:style styles/dapp-text}
        (i18n/label :t/dapp)]]]]))

(def browser-config
  (reader/read-string (slurp "./src/status_im/utils/browser_config.edn")))

(defn toolbar-content [{:keys [url] :as browser}]
  (let [url-text (atom url)]
    [react/view
     [react/view (styles/toolbar-content false)
      [react/text-input {:on-change-text    #(reset! url-text %)
                         :on-submit-editing #(re-frame/dispatch [:update-browser (assoc browser :url @url-text)])
                         :auto-focus        (not url)
                         :placeholder       (i18n/label :t/enter-url)
                         :auto-capitalize   :none
                         :auto-correct      false
                         :default-value     url
                         :style             styles/url-input}]]]))

(defn- web-view-error [_ code desc]
  (reagent/as-element
   [react/view styles/web-view-error
    [react/text {:style styles/web-view-error-text}
     (i18n/label :t/web-view-error)]
    [react/text {:style styles/web-view-error-text}
     (str code)]
    [react/text {:style styles/web-view-error-text}
     (str desc)]]))

(defn web-view-loading []
  (reagent/as-element
   [react/view styles/web-view-loading
    [components/activity-indicator {:animating true}]]))

(defn on-bridge-message [message browser]
  (let [{:strs [type navState]} (js->clj (.parse js/JSON message))
        {:strs [url title]} navState]
    (when (= type "navStateChange")
      (when (not= "about:blank" url)
        (re-frame/dispatch [:update-browser-on-nav-change browser url false])))))

(defn on-navigation-change [event browser]
  (let [{:strs [url loading]} (js->clj event)]
    (when (not= "about:blank" url)
      (re-frame/dispatch [:update-browser-on-nav-change browser url loading]))))

(defn get-inject-js [url]
  (let [domain-name (nth (re-find #"^\w+://(www\.)?([^/:]+)" url) 2)]
    (get (:inject-js browser-config) domain-name)))

(views/defview browser []
  (views/letsubs [webview    (atom nil)
                  {:keys [address]} [:get-current-account]
                  {:keys [dapp? browser-id name history-index history] :as browser} [:get-current-browser]
                  {:keys [error?]} [:get :browser/options]
                  rpc-url    [:get :rpc-url]
                  network-id [:get-network-id]]
    (let [can-go-back?    (not (zero? history-index))
          can-go-forward? (< history-index (dec (count history)))]
      [react/keyboard-avoiding-view styles/browser
       [status-bar/status-bar]
       [toolbar.view/toolbar {}
        [toolbar.view/nav-button-with-count
         (actions/close (fn []
                          (.sendToBridge @webview "navigate-to-blank")
                          (re-frame/dispatch [:navigate-back])
                          (when error?
                            (re-frame/dispatch [:remove-browser browser-id]))))]
        (if dapp?
          [toolbar-content-dapp name]
          [toolbar-content browser])]
       (let [url (when (and history-index history)
                   (nth history history-index))]
         [components.webview-bridge/webview-bridge
          {:ref                                   #(reset! webview %)
           :source                                {:uri url}
           :java-script-enabled                   true
           :bounces                               false
           :local-storage-enabled                 true
           :start-in-loading-state                true
           :render-error                          web-view-error
           :render-loading                        web-view-loading
           :on-bridge-message                     #(on-bridge-message % browser)
           ;:on-navigation-state-change            #(on-navigation-change % browser)
           :on-load                               #(re-frame/dispatch [:update-browser-options {:error? false}])
           :on-error                              #(re-frame/dispatch [:update-browser-options {:error? true}])
           :injected-on-start-loading-java-script (str js-res/web3
                                                       (get-inject-js url)
                                                       (js-res/web3-init
                                                        rpc-url
                                                        (ethereum/normalized-address address)
                                                        (str network-id)))
           :injected-java-script                  js-res/webview-js}])
       [react/view styles/toolbar
        [react/touchable-highlight {:on-press            #(re-frame/dispatch [:browser-nav-back browser])
                                    :disabled            (not can-go-back?)
                                    :style               (when (not can-go-back?) styles/disabled-button)
                                    :accessibility-label :previou-page-button}
         [react/view
          [vector-icons/icon :icons/arrow-left]]]
        [react/touchable-highlight {:on-press            #(re-frame/dispatch [:browser-nav-forward browser])
                                    :disabled            (not can-go-forward?)
                                    :style               (merge styles/forward-button
                                                                (when (not can-go-forward?) styles/disabled-button))
                                    :accessibility-label :next-page-button}
         [react/view
          [vector-icons/icon :icons/arrow-right]]]]
       (when-not dapp?
         [tooltip/bottom-tooltip-info
          (i18n/label :t/browser-warning)])])))
