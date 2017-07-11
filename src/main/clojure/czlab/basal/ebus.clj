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

  (:require [czlab.basal.log :as log]
            [clojure.java.io :as io]
            [clojure.string :as cs]
            [czlab.basal.meta :as m]
            [czlab.basal.core :as c]
            [czlab.basal.str :as s])

  (:import [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkSubSCR "" [topic selector context repeat? args]
  (doto {:target context
         :id "sub#" + Number(++_SEED)
         :repeat? repeat?
         :args (or args [])
         :action selector
         :topic topic
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
     {:tree {} :subs []})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn _listen "" [repeat? topics selector target more]
  ;; for each topic, subscribe to it.
  (let [ts (cs/split topics #"\s+")
        rc (map #(_addSub repeat? % selector target more) ts)]
    (filterv #(> (count %) 0) rc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn _doSub "" [node token]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Subscribe to 1+ topics, returning a list of subscriber handles.
;; topics => "/hello/*  /goodbye/*"
;; @memberof module:cherimoia/ebus~RvBus
;; @method once
;; @param {String} topics - space separated if more than one.
;; @param {Function} selector
;; @param {Object} target
;; @return {Array.String} - subscription ids
(defn once "" [topics selector target & args]
  (let [rc (apply _listen
                  this
                  false
                  topics selector target args)]
    (or rc [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; subscribe to 1+ topics, returning a list of subscriber handles.
;; topics => "/hello/*  /goodbye/*"
;; @memberof module:cherimoia/ebus~RvBus
;; @method on
;; @param {String} topics - space separated if more than one.
;; @param {Function} selector
;; @param {Object} target
;; @return {Array.String} - subscription ids.
(defn on "" [topics selector target & args]
  (or (apply _listen
             this
             true
             topics
             selector target args) []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Trigger event on this topic.
;; @memberof module:cherimoia/ebus~RvBus
;; @method fire
;; @param {String} topic
;; @param {Object} msg
;; @return {Boolean}
(defn fire "" [topic msg]
  (let [tokens (splitTopic topic)
        rc false]
    (if-not (empty? tokens)
      (_doPub this topic
                     this.root tokens 0
                     (or msg {})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn splitTopic "" [topic]
  (safeSplit topic "/"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Resume actions on this handle.
;; @memberof module:cherimoia/ebus~RvBus
;; @method resume
;; @param {Object} - handler id
(defn resume "" [handle]
  (if-some [sub (get this_subs handle)]
    (set! (:active? sub) true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Pause actions on this handle.
;; @memberof module:cherimoia/ebus~RvBus
;; @method pause
;; @param {Object} - handler id
(defn pause "" [handle]
  (if-some [sub (get this_subs handle)]
    (set! (:active? sub) false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Stop actions on this handle.
;; Unsubscribe.
;; @memberof module:cherimoia/ebus~RvBus
;; @method off
;; @param {Object} - handler id
(defn off "" [handle]
  (if-some [sub (get this_subs handle)]
    (_unSub this this.root
            (splitTopic sub.topic) 0 sub)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- iniz "" []
  (set! this.root (mkTreeNode true))
  (set! this.subs  {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remove all subscribers.
(defn removeAll "" [] (iniz))

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

