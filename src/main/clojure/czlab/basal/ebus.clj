;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.basal.ebus

  (:require [clojure.string :as cs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private _SEED (atom 1))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- splitTopic "" [topic] (if (and (string? topic)
                                      (not-empty topic))
                               (->> (cs/split topic #"/")
                                    (filterv #(if (> (count %) 0) %)))
                               []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nextSEQ "" [] (let [n @_SEED] (swap! _SEED inc) n))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol EventBus
  ""
  ())

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkSubSCR
  "" [topic repeat? listener args]
  {:pre [(fn? listener)]}
  {:id (keyword (str "s#" (nextSEQ)))
   :repeat? repeat?
   :action listener
   :topic topic
   :args args
   :status (long-array 1 1)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; nodes - children
;; subscribers
(defn- mkTreeNode "" [] {:nodes {} :subcs {}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addOneSub "" [node sub]
  (update-in node [:subcs] assoc (:id sub) sub))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- remOneSub "" [node sub]
  (update-in node [:subcs] dissoc (:id sub)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addOneTopic "" [top {:keys [topic] :as sub}]
  (let [path (splitTopic topic)]
    (-> (update-in top path addOneSub sub)
        (update-in [:subcs] assoc (:id sub) sub))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addTopic "" [root sub] (swap! root addOneTopic sub))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; for each topic, subscribe to it.
(defn- listen "" [root repeat? topics listener more]
  (->> (-> (or topics "") cs/trim (cs/split #"\s+"))
       (filter #(if (> (count %) 0) %))
       (mapv #(addTopic root
                        (mkSubSCR % repeat? listener more)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- delOneTopic "" [top {:keys [topic] :as sub}]
  (let [path (splitTopic topic)]
    (-> (update-in top path remOneSub sub)
        (update-in [:subcs] dissoc (:id sub)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- unSub "" [root sub] (swap! root delOneTopic sub))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doPub "" [branch pathTokens topic msg]
  (let [{:keys [nodes subcs]} branch
        [p & more] pathTokens
        cur (get nodes p)
        s1 (get nodes "*")
        s1c (:nodes s1)
        s2 (get nodes "**")]
    (if s2
      (run s2 topic msg))
    (if s1
      (cond
        (and (empty? more)
             (empty? s1c))
        (run s1 topic msg)
        (or (and (empty? s1c)
                 (not-empty more))
            (and (empty? more)
                 (not-empty s1c)))
        nil
        :else
        (doPub s1 more topic msg)))
    (if cur
      (if (not-empty more)
        (doPub cur more topic msg)
        (run cur topic msg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- run "" [{:keys [subcs]} topic msg]
  (doseq [z subcs
          :let [{:keys [repeat? action status extraArgs]} z]
          :when (pos? (aget ^longs status 0))]
    (apply action (concat [topic msg] extraArgs))
    ;;if one time only, turn off flag
    (if-not repeat?
      (aset ^longs status 0 -1))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn eventBus<> "" []
  (let [state (atom (mkTreeNode))]
    (reify EventBus
      ;; Subscribe to 1+ topics, returning a list of subscriber handles.
      ;; topics => "/hello/*  /goodbye/*"
      ;; @param {String} topics - space separated if more than one.
      ;; @return {Array.String} - subscription ids
      (once [_ topics listener & args]
        (or (listen state false topics listener args) []))
      ;; subscribe to 1+ topics, returning a list of subscriber handles.
      ;; topics => "/hello/*  /goodbye/*"
      ;; @param {String} topics - space separated if more than one.
      ;; @param {Function} selector
      ;; @return {Array.String} - subscription ids.
      (on [_ topics listener & args]
        (or (listen state true topics listener args) []))
      ;; Trigger event on this topic.
      ;; @param {String} topic
      ;; @param {Object} msg
      ;; @return {Boolean}
      (fire [_ topic msg]
        (let [tokens (splitTopic topic)]
          (if (not-empty tokens)
            (doPub @state tokens topic msg))))
      ;; Resume actions on this handle.
      ;; @memberof module:cherimoia/ebus~RvBus
      ;; @method resume
      ;; @param {Object} - handler id
      (resume [_ handle]
        (let [sub (get (:subcs @state) handle)
              r? (true? (:repeat? sub))
              st (if sub (:status sub))
              sv (if st (aget ^longs st 0) -911)]
          (if (= 0 sv)
            (aset ^longs st 0 1))))
      ;; Pause actions on this handle.
      ;; @memberof module:cherimoia/ebus~RvBus
      ;; @method pause
      ;; @param {Object} - handler id
      (pause [_ handle]
        (let [sub (get (:subcs @state) handle)
              st (if sub (:status sub))
              sv (if st (aget ^longs st 0) -911)]
          (if (pos? sv)
            (aset ^longs st 0 0))))
      ;; Stop actions on this handle.
      ;; @memberof module:cherimoia/ebus~RvBus
      ;; @method off
      ;; @param {Object} - handler id
      (off [_ handle]
        (if-some [sub (get (:subcs @state) handle)]
          (unSub state sub)))
      ;; Remove all subscribers.
      (removeAll [_] (reset! state (mkTreeNode))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

