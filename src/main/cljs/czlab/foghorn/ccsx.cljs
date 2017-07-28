;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.foghorn.ccsx

  (:require-macros
    [czlab.foghorn.macros
     :refer [cc-director
             cc-evtMgr
             cc-view
             cc-sys
             native?
             div2]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-infer* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:dynamic *main* (atom nil))

(def ^:dynamic *anchors* {:center [0.5 0.5]
                          :top [0.5  1]
                          :topRight [1 1]
                          :right [1 0.5]
                          :bottomRight [1 0]
                          :bottom [0.5 0]
                          :bottomLeft [0 0]
                          :left [0 0.5]
                          :topLeft [0 1]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pointInBox? ""
  ([box pt] (pointInBox? box (:x pt) (:y pt)))
  ([box x y]
   (and (>= x (:left box))
        (<= x (:right box))
        (>= y (:bottom box))
        (<= y (:top box)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getHeight "" [sprite]
  (.-height (.getContentSize sprite)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getScaledHeight "" [sprite]
  (* (getHeight sprite)
     (.getScaleY sprite)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getWidth "" [sprite]
  (.-width (.getContentSize sprite)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getScaledWidth "" [sprite]
  (* (getWidth sprite)
     (.getScaleX sprite)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLeft "" [sprite]
  (- (.-x (.getPosition sprite))
     (div2 (getWidth sprite))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getRight "" [sprite]
  (+ (.-x (.getPosition sprite))
     (div2 (getWidth sprite))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getTop "" [sprite]
  (+ (.-y (.getPosition sprite))
     (div2 (getHeight sprite))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getBottom "" [sprite]
  (- (.-y (.getPosition sprite))
     (div2 (getHeight sprite))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLastLeft "" [ent]
  (if-some [p (:lastPos ent)]
    (- (:x p) (div2 (getWidth (:sprite ent))))
    (getLeft ent)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLastRight "" [ent]
  (if-some [p (:lastPos ent)]
    (+ (:x p)
       (div2 (getWidth (:sprite ent))))
    (getRight ent)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLastTop "" [ent]
  (if-some [p (:lastPos ent)]
    (+ (:y p)
       (div2 (getHeight (:sprite ent))))
    (getTop ent)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLastBottom "" [ent]
  (if-some [p (:lastPos ent)]
    (- (:y p)
       (div2 (getHeight (:sprite ent))))
    (getBottom ent)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bbox4 "" [s]
  (doto
    {:bottom (getBottom s)
     :top (getTop s)
     :left (getLeft s)
     :right (getRight s)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn halfHW "" [sprite]
  (let [z (.getContentSize sprite)]
    [(div2 (.width z)) (div2 (.height z))]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bbox "" [sprite]
  (-> js/cc
      (.rect (getLeft sprite)
             (getBottom sprite)
             (getWidth sprite)
             (getHeight sprite))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bbox4b4 "" [ent]
  (doto
    {:bottom (getLastBottom ent)
     :top (getLastTop ent)
     :left (getLastLeft ent)
     :right (getLastRight ent)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn vbox "" []
  (let [v (cc-view)
        vo  (.getVisibleOrigin v)
        wz (.getVisibleSize v)]
    {:bottom (.-y vo)
     :left (.-x vo)
     :right (+ (.-x vo) (.-width wz))
     :top (+ (.-y vo) (.-height wz))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn vrect "" []
  (let [v (cc-view)
        vo (.getVisibleOrigin v)
        wz (.getVisibleSize v)]
    {:x (.-x vo)
     :y (.-y vo)
     :width (.-width wz)
     :height (.-height wz)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn collide0 "" [spriteA spriteB]
  (if (and spriteA spriteB)
    (.rectIntersectsRect js/cc
                         (bbox spriteA)
                         (bbox spriteB))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn collide? "" [a b]
  (if (and a b)
    (collide0 (:sprite a) (:sprite b))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn setDevRes "" [landscape? w h pcy]
  (let [[a b] (if landscape? [w h] [h w])]
    (-> js/cc
        (.-view)
        (.setDesignResolutionSize a b pcy))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn undoTimer "" [par tm]
  (if (and (native?) tm) (.release tm)) nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createTimer "" [par tm]
  (let [rc (.runAction par (js/cc.DelayTime. tm))]
    (if (native?) (.retain rc))
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn timerDone? "" [t] (and t (.isDone t)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createSprite "" [name]
  (doto (js/cc.Sprite.)
    (.initWithSpriteFrameName name)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runScene ""
  ([nx] (runScene nx 0.6))
  ([nx delays]
    (-> (cc-director)
        (.runScene  (js/cc.TransitionCrossFade. delays nx)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn isTransitioning? "" []
  (instance?
    js/cc.TransitionScene (-> (cc-director) (.getRunningScene))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn csize "" [frame]
  (-> (createSprite frame) (.getContentSize)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn center "" []
  (let [rc (vrect)]
    {:x (+ (:x rc) (div2 (:width rc)))
     :y (+ (:y rc) (div2 (:height rc)))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn centerX "" [] (:x (center)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn centerY "" [] (:y (center)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn screen "" []
  (let [rc (if (native?)
             (.getFrameSize (cc-view))
             (.getWinSize (cc-director)))]
    {:width (.-width rc)
     :height (.-height rc)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn screenHeight "" [] (:height (screen)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn screenWidth "" [] (:width (screen)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn scenter "" []
  (let [z (screen)]
    {:x (div2 (:width z))
     :y (div2 (:height z))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn vboxMID "" [box]
  (doto
    {:x (+ (:left box)
           (div2 (- (:right box) (:left box))))
    :y (+ (:bottom box)
          (div2 (- (:top box) (:bottom box))))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn isPortrait? "" []
  (let [s (screen)]
    (> (:height s) (:width s))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn outOfBound? "" [ent B]
  (if ent
    (outOfBound? (bbox4 (:sprite ent)) (or B (vbox)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn traceEnclosure
  "Test if this box is hitting boundaries.
  rect.x & y are center positioned.
  If hit, the new position and velocities
  are returned."
  [dt,bbox,rect,vel]
  (let
    [sz (div2 (:height rect))
     sw (div2 (:width rect))
     vx (:x vel)
     vy (:y vel)
     y (+ (:y rect)  (* dt (:y vel)))
     x (+ (:x rect) (* dt (:x vel)))
     hit? false
     [y vy hit?]
     (cond
       (> (+ y sz) (:top bbox))
       ;;hitting top wall
       [(- (:top bbox) sz) (- vy) true]
       (< (- y sz) (:bottom bbox))
       ;;hitting bottom wall
       [(+ (:bottom bbox) sz) (- vy) true]
       :else
       [y vy hit?])
     [x vx hit?]
     (cond
       (> (+ x sw)  (:right bbox))
       ;;hitting right wall
       [(- (:right bbox) sw) (- vx) true]
       (< (- x sw)  (:left bbox))
       ;;hitting left wall
       [(+ (:left bbox) sw) (- vx) true]
       :else
       [x vx hit?])]
    (if hit?
      {:hit? true
       :x x
       :y y
       :vx vx
       :vy vy}
      {:hit? false
       :x x
       :y y})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getSprite
  "Get the sprite from the frame cache using
  its id (e.g. #ship)."
  [frameid]
  (-> js/cc
      (.-spriteFrameCache)
      (.getSpriteFrame frameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn hasKeyPad? "" []
  (and (not (native?))
       (aget (.capabilities (cc-sys)) "keyboard")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onKeyPolls "" [kb]
  (when (hasKeyPad?)
    (-> (cc-evtMgr)
        (.addListener
          (clj->js
            {:onKeyPressed (fn [kee e] (aset kb kee true))
             :onKeyReleased (fn [kee e] (aset kb kee false))
             :event js/cc.EventListener.KEYBOARD })
          @*main*))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onKeys "" [bus]
  (when (hasKeyPad?)
    (-> (cc-evtMgr)
        (.addListener
          (clj->js
            {:onKeyPressed
             (fn [kee e]
               (.fire bus
                      "/key/down" {:group :key :key kee :event e}))
             :onKeyReleased
             (fn [kee e]
               (.fire bus "/key/up" {:group :key :key kee :event e}))
             :event js/cc.EventListener.KEYBOARD})
          @*main*))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn hasMouse? "" []
  (some? (some-> (.capabilities (cc-sys))
                 (aget "mouse"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onMouse "" [bus]
  (when (hasMouse?)
    (-> (cc-evtMgr)
        (.addListener
          (clj->js
            {:onMouseMove
             (fn [e]
               (if (= (.getButton e)
                      (js/cc.EventMouse.BUTTON_LEFT))
                 (.fire bus
                        "/mouse/move"
                        {:group :mouse
                         :loc (.getLocation e)
                         :delta (.getDelta e)
                         :event e})))
             :onMouseDown
             (fn [e]
               (.fire bus
                      "/mouse/down"
                      {:group :mouse
                       :loc (.getLocation e)
                       :event e}))
             :onMouseUp
             (fn [e]
               (.fire bus
                      "/mouse/up"
                      {:group :mouse
                       :loc (.getLocation e)
                       :event e}))
             :event js/cc.EventListener.MOUSE})
          @*main*))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn hasTouch? "" []
  (some? (some-> (.capabilities (cc-sys))
                 (aget "touches"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- touchAllListener<> "" [bus]
  (clj->js
    {:event js/cc.EventListener.TOUCH_ALL_AT_ONCE
     :prevTouchId -1
     :onTouchesBegan (fn [ts e] true)
     :onTouchesEnded
     (fn [ts e]
       (.fire bus
              "/touch/all/end"
              {:group :touch
               :event e
               :loc (.getLocation (aget ts 0))}))
     :onTouchesMoved
     (fn [ts e]
       (let [id (.id (aget ts 0))]
         (if (not= id (.-prevTouchId this))
           (set! (.-prevTouchId this) id)
           (.fire bus
                  "/touch/all/move"
                  {:group :touch
                   :event e
                   :delta (.getDelta (aget ts 0))}))))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onTouchAll "" [bus]
  (when (hasTouch?)
    (-> (cc-evtMgr)
        (.addListener (touchAllListener<> bus) @*main*))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- touchOneListener<> "" [bus]
  (clj->js
    {:event js/cc.EventListener.TOUCH_ONE_BY_ONE
     :swallowTouches true
     :onTouchBegan (fn [t e] true)
     :onTouchMoved
     (fn [t e]
       (.fire bus
              "/touch/one/move"
              {:group :touch
               :event e
               :delta (.getDelta t)
               :loc (.getLocation t)}))
     :onTouchEnded
     (fn [t e]
       (.fire bus
              "/touch/one/end"
              {:group :touch
               :event e
               :loc (.getLocation t)}))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onTouchOne "" [bus]
  (when (hasTouch?)
    (-> (cc-evtMgr)
        (.addListener (touchOneListener<> bus) @*main*))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resolveElastic "" [obj1 obj2]
  (let
    [pos2 (.getPosition (:sprite obj2))
     pos1 (.getPosition (:sprite obj1))
     sz2 (.getContentSize (:sprite obj2))
     sz1 (.getContentSize (:sprite obj1))
     hh1 (div2 (.-height sz1))
     hw1 (div2 (.-width sz1))
     x (.-x pos1)
     y (.-y pos1)
     bx2 (bbox4 (:sprite obj2))
     bx1 (bbox4 (:sprite obj1))
     [x y]
     (cond
       ;;coming from right
       (and (< (.-left bx1)
               (.-right bx2))
            (< (.-right bx2)
               (.-right bx1)))
       (do
         (set! (.-x (:vel obj1))
               (.abs js/Math (.-x (:vel obj1))))
         (set! (.-x (:vel obj2))
               (- (.abs js/Math (.-x (:vel obj2)))))
         [(+ (getRight (:sprite obj2)) hw1) y])
       ;;coming from left
       (and (> (.-right bx1)
               (.-left bx2))
            (< (.-left bx1)
               (.-left bx2)))
       (do
         (set! (.-x (:vel obj1))
               ( - (.abs js/Math (.-x (:vel obj1)))))
         (set! (.-x (:vel obj2))
               (.abs js/Math (.-x (:vel obj2))))
         [(- (getLeft (:sprite obj2)) hw1) y])
       ;;coming from top
       (and (< (:bottom bx1)
               (:top bx2))
            (> (:top bx1)
               (:top bx2)))
       (do
         (set! (.-y (:vel obj1))
               (.abs js/Math (.-y (:vel obj1))))
         (set! (.-y (:vel obj2))
               (- (.abs js/Math (.-y (:vel obj2)))))
         [x (+ (getTop (:sprite obj2)) hh1)])
       ;;coming from bottom
       (and (> (:top bx1)
               (:bottom bx2))
            (> (:bottom bx2)
               (:bottom bx1)))
       (do
         (set! (.-y (:vel obj1))
               (- (.abs js/Math (.-y (:vel obj1)))))
         (set! (.-y (:vel obj2))
               (.abs js/Math (.-y (:vel obj2))))
         [x (- (getBottom (:sprite obj2)) hh1)])
       :else
       [x y])]
    (.updatePosition obj1 x y)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn tmenu
  "Create a text menu containing this set of items.
  Each item has the form
  {:text
   :fontPath
   :func
   :target}"
  ([items] (tmenu 1))
  ([items scale]
   (let [menu (js/cc.Menu.)
         t (atom 1)]
    (doseq [obj items
            :let [lbl (js/cc.LabelBMFont. (:text obj)
                                          (:fontPath obj))
                  mi (js/cc.MenuItemLabel lbl
                                          (:func obj)
                                          (:target obj))]]
      (doto mi
        (.setOpacity (* 255  0.9))
        (.setScale (or scale 1))
        (.setTag @t))
      (swap! t inc)
      (.addChild menu mi))
    menu)))

  /**
   * Make a text label menu containing one single button.
   * @method
   * @param {Object} options
   * @return {cc.Menu}
   */
  tmenu1(options) {
    let menu = this.tmenu(options);
    if (options.anchor) { menu.setAnchorPoint(options.anchor); }
    if (options.pos) { menu.setPosition(options.pos); }
    if (options.visible === false) { menu.setVisible(false); }
    menu.alignItemsVertically();
    return menu;
  },

  /**
   * Create a vertically aligned menu with graphic buttons.
   * @method
   * @param {Array} items
   * @param {Object} options
   * @return {cc.Menu}
   */
  vmenu(items, options) {
    const hint=options || {},
    m= this.pmenu(true,
                  items,
                  hint.scale,
                  hint.padding);
    if (!!hint.pos) {
      m.setPosition(hint.pos);
    }
    return m;
  },

  /**
   * Create a horizontally aligned menu with graphic buttons.
   * @method
   * @param {Array} items
   * @param {Object} options
   * @return {cc.Menu}
   */
  hmenu(items, options) {
    const hint= options || {},
    m= this.pmenu(false,
                  items,
                  hint.scale,
                  hint.padding);
    if (!!hint.pos) {
      m.setPosition(hint.pos);
    }
    return m;
  },

  /**
   * Create a menu with graphic buttons.
   * @method
   * @param {Boolean} vertical
   * @param {Array} items
   * @param {Number} scale
   * @param {Number} padding
   * @return {cc.Menu}
   */
  pmenu(vertical, items, scale, padding) {
    let menu = new cc.Menu(),
    obj,
    mi,
    t=0;

    for (let n=0; n < items.length; ++n) {
      obj=items[n];
      mi= new cc.MenuItemSprite(new cc.Sprite(obj.nnn),
                                new cc.Sprite(obj.sss || obj.nnn),
                                new cc.Sprite(obj.ddd || obj.nnn),
                                obj.selector || obj.cb,
                                obj.target);
      if (!!obj.color) { mi.setColor(obj.color); }
      if (!!scale) { mi.setScale(scale); }
      mi.setTag(++t);
      menu.addChild(mi);
    }

    padding = padding || 10;
    if (!vertical) {
      menu.alignItemsHorizontallyWithPadding(padding);
    } else {
      menu.alignItemsVerticallyWithPadding(padding);
    }

    return menu;
  },

  /**
   * Create a single button menu.
   * @method
   * @param {Object} options
   * @return {cc.Menu}
   */
  pmenu1(options) {
    const menu = this.pmenu(true, [options]);
    if (options.anchor) { menu.setAnchorPoint(options.anchor); }
    if (options.pos) { menu.setPosition(options.pos); }
    if (options.visible === false) { menu.setVisible(false); }
    return menu;
  },

  /**
   * Create a Label.
   * @method
   * @param {Object} options
   * @return {cc.LabelBMFont}
   */
  bmfLabel(options) {
    let f= new cc.LabelBMFont(options.text, options.fontPath);
    if (options.color) { f.setColor(options.color); }
    if (options.pos) { f.setPosition(options.pos); }
    if (options.anchor) { f.setAnchorPoint(options.anchor); }
    if (options.visible === false) { f.setVisible(false); }
    f.setScale( options.scale || 1);
    f.setOpacity(0.9*255);
    return f;
  }

};




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


