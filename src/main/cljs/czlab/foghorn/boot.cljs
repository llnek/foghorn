;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.foghorn.boot

  (:require [czlab.foghorn.asterix :as asx]
            [czlab.foghorn.log :as log]
            [czlab.foghorn.ccsx :as cx]))

;;let ss1= xcfg.game.start || 'StartScreen',
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-infer* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleMultiDevicesEx
  (let [searchPaths (.getSearchPaths js/jsb.fileUtils)
        landscape? (get-in cfg [:game :landscape?])
        pcy (get-in cfg [:resolution :policy])
        {:keys [width height]}
        (cx/screenSize)
        _ (.log js/cc
                (str "view.frameSize = ["
                     width ", " height "]"))
        [x y res]
        (cond
          (or (>= width 2048)
              (>= height 2048))
          [2048 1536 "hdr"]
          (or (>= width 1136)
              (>= height 1136))
          [1136 640 "hds"]
          (or (>= width 1024)
              (>= height 1024))
          [1024 768 "hds"]
          (or (>= width 960)
              (>= height 960))
          [960 640 "hds"]
          :else
          [480 320 "sd"])]
    (set! (.-resDir (:resolution cfg)) res)
    (cx/setDeviceRes landscape? x y pcy)
    ;;need to prefix "assets" for andriod
    (doseq [p [(str "assets/res/" res)
               (str "res/" res) "assets/src" "src"]]
      (.push searchPaths p))
    (.log js/cc (str "Resource search path: " res))
  searchPaths))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleMultiDevices
  "Sort out what resolution to use for this device.
  return {Array} - search paths"
  [cfg]
  ;;if handler provided, call it and go.
  (let [f (:handleDevices cfg)]
    (if (fn? f) (f cfg) (handleMultiDevicesEx cfg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pvLoadSound "" [cfg k v]
  (asx/sanitizeUrl (str v  "." (get-in cfg [:game :sfx]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pvLoadSprite "" [cfg k v] (asx/sanitizeUrl (aget v 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pvLoadImage "" [cfg k v] (asx/sanitizeUrl v))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pvLoadTile "" [cfg k v] (asx/sanitizeUrl v))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pvLoadAtlas "" [cfg k v]
  [(asx/sanitizeUrl (str v  ".plist"))
   (asx/sanitizeUrl (str v  ".png"))])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pvLoadLevels "" [cfg])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pvGatherPreloads "" [cfg])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;class MyLoaderScene
const MyLoaderScene = cc.Scene.extend({

  init() { return true; },

  _startLoading() {
    const res = this.resources,
    self=this;

    self.unschedule(self._startLoading);
    cc.loader.load(res,
                   (result, count, loadedCount) => {},
                   () => {
                     if (sjs.isfunc(self.cb)) {
                       self.cb();
                     }
                   });
  },

  initWithResources(resources, cb) {
    this.resources = resources || [];
    this.cb = cb;
  },

  onEnter() {
    const self = this;
    cc.Node.prototype.onEnter.call(self);
    self.schedule(self._startLoading, 0.3);
  },

  onExit() {
    cc.Node.prototype.onExit.call(this);
  }

});

//////////////////////////////////////////////////////////////////////////////
let preLaunchApp = (sjs, sh, xcfg, ldr,  ss1) => {
  let fz= ccsx.screen(),
  paths,
  sz,
  pfx,
  rs, pcy;

  if (cc.sys.isNative) {
    paths= handleMultiDevices();
    if (!!paths) {
      jsb.fileUtils.setSearchPaths(paths);
    }
  } else {
    sz= xcfg.game[xcfg.resolution.resDir];
    pcy = xcfg.resolution.web;
    cc.view.setDesignResolutionSize(sz.width, sz.height, pcy);
  }

  rs= cc.view.getDesignResolutionSize();
  xcfg.handleResolution(rs);
  sjs.loggr.info('DesignResolution, = [' +
                 rs.width + ", " +
                 rs.height + "]" +
                 ", scale = " + xcfg.game.scale);

  cc.director.setProjection(cc.Director.PROJECTION_2D);
  if (cc.sys.isNative) {
    pfx= "";
  } else {
    cc.view.resizeWithBrowserSize(true);
    cc.view.adjustViewPort(true);
    pfx = "/public/ig/res/";
  }

  //cc.director.setAnimationInterval(1 / sh.xcfg.game.frameRate);
  if (xcfg.game.debug) {
    cc.director.setDisplayStats(xcfg.game.showFPS);
  }

  rs= [ pfx + 'cocos2d/pics/preloader_bar.png',
        pfx + 'cocos2d/pics/ZotohLab.png' ];
  // hack to suppress the showing of cocos2d's logo
  cc.loaderScene = new MyLoaderScene();
  cc.loaderScene.init();
  cc.loaderScene.initWithResources(rs, () => {
    ldr.preload(pvGatherPreloads(sjs, sh, xcfg), () => {
      xcfg.runOnce();
      cc.director.runScene( sh.protos[ss1].reify() );
    });
  });
  cc.director.runScene(cc.loaderScene);
}

sjs.loggr.info("About to create Cocos2D HTML5 Game");

preLaunchApp(sjs, sh, xcfg, loader, ss1);
sh.l10nInit(),
sh.sfxInit();

//sjs.merge(me.xcfg.game, global.document.ccConfig);
sjs.loggr.debug(sjs.jsonfy(xcfg.game));
sjs.loggr.info("Registered game start state - " + ss1);
sjs.loggr.info("Loaded and running. OK");

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


