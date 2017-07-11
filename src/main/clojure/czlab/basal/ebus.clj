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
                               (cs/split topic #"/")))

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
  "" [topic repeat? listener & args]
  {:pre [(fn? listener)]}
  (atom
    {:id (keyword (str "s#" (nextSEQ)))
     :repeat? repeat?
     :action listener
     :topic topic
     :args args
     :active? true}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkTreeNode ""
  ([] (mkTreeNode nil))
  ([root?]
   (if root?
     ;; children - branches
     ;; subscribers
     {:tree {}}
     {:tree {} :subs {}})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; for each topic, subscribe to it.
(defn- listen "" [repeat? topics listener more]
  (->> (cs/split (or topics "") #"\s+")
       (map #(addSub repeat? % listener more))
       (filterv #(if (> (count %) 0) %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doSub "" [node token]
  (if-not (contains? (:tree node) token)
    (update-in node
               [:tree]
               assoc token (mkTreeNode)))
  (get (:tree node) token))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn _addSub "" [repeat? topic selector target more]
  (let [tkns (splitTopic topic)]
    (when-not (empty? tkns)
      (let [rc (mkSubSCR topic selector target repeat? more)
            node (reduce #(_doSub %1 %2) this_root tkns)]
        (update-in this_subs
                   assoc (:id rc) rc)
        (update-in node
                   [:subs] conj rc)
        (:id rc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn _unSub "" [node tokens pos sub]
  (if node (_unSub node tokens pos sub)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn _unSub "" [node tokens pos sub]
  (if (< pos (count tokens))
    (let [k (aget tokens pos)
          cn (get (:tree node) k)
          _ (_unSub this cn tokens (inc pos) sub)]
      (if (and (empty? (:tree cn))
               (empty? (:subs cn)))
        (delete (get (:tree node) k))))
    (let [sid (:id sub)
          pos -1]
      (some
        #(let [pos (inc pos)]
           (when (= (:id %) sid)
             (delete (get this_subs (:id %)))
             (.splice node_subs pos 1)
             true))
        node_subs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn _doPub "" [topic node tokens pos msg]
  (if node (_doPub topic node tokens pos msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn _doPub "" [topic node tokens pos msg]
  (let [rc false]
    (if (< pos (count tokens))
      (let [rc (or rc
                   (_doPub this
                           topic
                           node.tree[ tokens[pos] ]
                           tokens
                           (inc pos) msg))
            rc (or rc
                   (_doPub this
                           topic
                           node.tree['*']
                           tokens
                           (inc pos) msg))]
        rc)
      (or rc
          (_run this topic  node msg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn _run "" [topic node msg]
  (let [cs (if node (:subs node) [])
        purge false
        rc false]
    (doseq [z cs]
      (when (and (:active? z)
                 (:action z))
        ;; pass along any extra parameters, if any.
        ((:action z) (:target z)
                     (concat [msg topic] z.args))
        ;; if once only, kill it.
        (when-not (:repeat? z)
          (delete this.subs[z.id])
          (set! (:active? z) false)
          (set! (:action z) nil)
          (set! purge true))
        (set! rc true)))
    ;; get rid of unwanted ones,
    ;; and reassign new set to the node.
    (if (and purge
             (not-empty cs))
      (set! node_subs
            (filterv #(if (:action %) %) cs)))
    rc))

(defn- iniz "" []
  (set! this.root (mkTreeNode true))
  (set! this.subs  {}))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn eventBus<> "" [state]
  (let []
    (reify EventBus
      ;; Subscribe to 1+ topics, returning a list of subscriber handles.
      ;; topics => "/hello/*  /goodbye/*"
      ;; @param {String} topics - space separated if more than one.
      ;; @return {Array.String} - subscription ids
      (once [_ topics listener & args]
        (or (apply _listen state false topics listener args) []))
      ;; subscribe to 1+ topics, returning a list of subscriber handles.
      ;; topics => "/hello/*  /goodbye/*"
      ;; @param {String} topics - space separated if more than one.
      ;; @param {Function} selector
      ;; @return {Array.String} - subscription ids.
      (on [_ topics selector & args]
        (or (apply _listen state true topics selector args) []))
      ;; Trigger event on this topic.
      ;; @param {String} topic
      ;; @param {Object} msg
      ;; @return {Boolean}
      (fire [_ topic msg]
        (let [tokens (splitTopic topic)]
          (if-not (empty? tokens)
            (_doPub state topic tokens 0 (or msg {})))))
      ;; Resume actions on this handle.
      ;; @memberof module:cherimoia/ebus~RvBus
      ;; @method resume
      ;; @param {Object} - handler id
      (resume [_ handle]
        (if-some [sub (get (:subs @state) handle)]
          (swap! sub
                 assoc :active? true)))
      ;; Pause actions on this handle.
      ;; @memberof module:cherimoia/ebus~RvBus
      ;; @method pause
      ;; @param {Object} - handler id
      (pause [_ handle]
        (if-some [sub (get (:subs @state) handle)]
          (swap! sub
                 assoc :active? false)))
      ;; Stop actions on this handle.
      ;; @memberof module:cherimoia/ebus~RvBus
      ;; @method off
      ;; @param {Object} - handler id
      (off [_ handle]
        (if-some [sub (get (:subs @state) handle)]
          (_unSub state (splitTopic (:topic @sub)) 0 sub)))
      ;; Remove all subscribers.
      (removeAll [_] (iniz state))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Trigger event on this topic.
;; @memberof module:cherimoia/ebus~EventBus
;; @method fire
;; @param {String} topic
;; @param {Object} msg
;; @return {Boolean}
(defn fire "" [topic msg]
  (_run this
        topic
        this.root.tree[topic] (or msg {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn splitTopic "" [topic] (doto [topic]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

