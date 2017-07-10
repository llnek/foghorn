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
  (let [ts topics.trim().split(/\s+/)
        rc (map #(_addSub repeat? % selector target more) ts)]
  return R.reject( z => { return z.length===0; }, rc);
}

//////////////////////////////////////////////////////////////////////////////
const _doSub = (node,token) => {
  if (! sjs.hasKey(node.tree, token)) {
    node.tree[token] = mkTreeNode();
  }
  return node.tree[token];
}

//////////////////////////////////////////////////////////////////////////////
const _addSub = function(repeat, topic, selector, target, more) {
  let tkns= this.splitTopic(topic),
  rcid='';
  if (tkns.length > 0) {
    const rc= mkSubSCR(topic, selector, target, repeat, more),
    node= R.reduce((memo, z) => {
      return _doSub(memo,z);
    },
    this.root, tkns);

    this.subs[rc.id] = rc;
    node.subs.push(rc);
    rcid= rc.id;
  }
  return rcid;
}

//////////////////////////////////////////////////////////////////////////////
const _unSub = function(node, tokens, pos, sub) {
  if (! sjs.echt(node)) { return; }
  if (pos < tokens.length) {
    const k= tokens[pos],
    cn= node.tree[k];
    _unSub.call(this, cn, tokens, pos+1, sub);
    if (R.keys(cn.tree).length === 0 &&
        cn.subs.length === 0) {
      delete node.tree[k];
    }
  } else {
    pos = -1;
    R.find( z => {
      pos += 1;
      if (z.id === sub.id) {
        delete this.subs[z.id];
        node.subs.splice(pos,1);
        return true;
      }
    }, node.subs);
  }
}

//////////////////////////////////////////////////////////////////////////////
const _doPub = function(topic, node, tokens, pos, msg) {
  if (! sjs.echt(node)) { return false; }
  let rc=false;
  if (pos < tokens.length) {
    rc = rc || _doPub.call(this,topic, node.tree[ tokens[pos] ], tokens, pos+1, msg);
    rc = rc || _doPub.call(this,topic, node.tree['*'], tokens, pos+1,msg);
  } else {
    rc = rc || _run.call(this,topic, node,msg);
  }
  return rc;
}

//////////////////////////////////////////////////////////////////////////////
const _run = function(topic, node, msg) {
  const cs= !!node ? node.subs : [];
  let purge=false,
  rc=false;

  R.forEach( z => {
    if (z.active &&
        sjs.echt(z.action)) {
      // pass along any extra parameters, if any.
      z.action.apply(z.target, [msg,topic].concat(z.args));
      // if once only, kill it.
      if (!z.repeat) {
        delete this.subs[z.id];
        z.active= false;
        z.action= null;
        purge=true;
      }
      rc = true;
    }
  }, cs);

  // get rid of unwanted ones, and reassign new set to the node.
  if (purge && cs.length > 0) {
    node.subs= R.filter( z => {
      return z.action ? true : false;
    }, cs);
  }

  return rc;
}

//////////////////////////////////////////////////////////////////////////////
/** @class RvBus */
class RvBus extends sjs.ES6Claxx {
  /**
   * Subscribe to 1+ topics, returning a list of subscriber handles.
   * topics => "/hello/*  /goodbye/*"
   * @memberof module:cherimoia/ebus~RvBus
   * @method once
   * @param {String} topics - space separated if more than one.
   * @param {Function} selector
   * @param {Object} target
   * @return {Array.String} - subscription ids
   */
  once(topics, selector, target /*, more args */) {
    const rc= _listen.call(this,false, topics,
                            selector, target,
                            sjs.dropArgs(arguments,3));
    return sjs.echt(rc) ? rc : [];
  }

  /**
   * subscribe to 1+ topics, returning a list of subscriber handles.
   * topics => "/hello/*  /goodbye/*"
   * @memberof module:cherimoia/ebus~RvBus
   * @method on
   * @param {String} topics - space separated if more than one.
   * @param {Function} selector
   * @param {Object} target
   * @return {Array.String} - subscription ids.
   */
  on(topics, selector, target /*, more args */) {
    const rc= _listen.call(this,true, topics,
                            selector, target,
                            sjs.dropArgs(arguments,3));
    return sjs.echt(rc) ? rc : [];
  }

  /**
   * Trigger event on this topic.
   * @memberof module:cherimoia/ebus~RvBus
   * @method fire
   * @param {String} topic
   * @param {Object} msg
   * @return {Boolean}
   */
  fire(topic, msg) {
    let tokens= this.splitTopic(topic),
    rc=false;
    if (tokens.length > 0 ) {
      rc= _doPub.call(this,topic,
                       this.root, tokens, 0, msg || {} );
    }
    return rc;
  }

  /**
   * @method splitTopic
   * @protected
   */
  splitTopic(topic) {
    return sjs.safeSplit(topic,'/');
  }

  /**
   * Resume actions on this handle.
   * @memberof module:cherimoia/ebus~RvBus
   * @method resume
   * @param {Object} - handler id
   */
  resume(handle) {
    const sub= this.subs[handle];
    if (!!sub) {
      sub.active=true;
    }
  }

  /**
   * Pause actions on this handle.
   * @memberof module:cherimoia/ebus~RvBus
   * @method pause
   * @param {Object} - handler id
   */
  pause(handle) {
    const sub= this.subs[handle];
    if (!!sub) {
      sub.active=false;
    }
  }

  /**
   * Stop actions on this handle.
   * Unsubscribe.
   * @memberof module:cherimoia/ebus~RvBus
   * @method off
   * @param {Object} - handler id
   */
  off(handle) {
    const sub= this.subs[handle];
    if (!!sub) {
      _unSub.call(this,this.root,
                   this.splitTopic(sub.topic), 0, sub);
    }
  }

  /**
   * @method iniz
   * @private
   */
  iniz() {
    this.root= mkTreeNode(true);
    this.subs = {};
  }

  /**
   * Remove all subscribers.
   * @memberof module:cherimoia/ebus~RvBus
   * @method removeAll
   */
  removeAll() {
    this.iniz();
  }

  /**
   * @method constructor
   * @private
   */
  constructor() {
    super();
    this.iniz();
  }

};
//////////////////////////////////////////////////////////////////////////////
/**
 * @class EventBus
 */
class EventBus extends RvBus {
  /**
   * Trigger event on this topic.
   * @memberof module:cherimoia/ebus~EventBus
   * @method fire
   * @param {String} topic
   * @param {Object} msg
   * @return {Boolean}
   */
  fire(topic, msg) {
    return _run.call(this, topic,
                     this.root.tree[topic], msg || {});
  }

  /**
   * @method splitTopic
   * @protected
   */
  splitTopic(topic) { return [topic]; }

  /**
   * @method constructor
   * @private
   */
  constructor() {
    super();
  }
}

/** @alias module:cherimoia/ebus */
const xbox= /** @lends xbox# */{
  /**
   * @method reifyRvBus
   * @return {RvBus}
   */
  reifyRvBus() {
    return new RvBus();
  },
  /**
   * @method reify
   * @return {EventBus}
   */
  reify() {
    return new EventBus();
  },
  /**
   * @property {EventBus} EventBus
   */
  EventBus: EventBus,
  /**
   * @property {RvBus} RvBus
   */
  RvBus: RvBus
};


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

