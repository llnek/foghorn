;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

    czlab.foghorn.asterix
  (:require-macros
    [czlab.foghorn.macros :refer [now-millis]])
  (:require [czlab.foghorn.l10n :as lz]
            [clojure.string :as cs]
            [czlab.foghorn.log :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:dynamic *config* (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private _seed_counter_ (atom 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- next-seed "" []
  (let [n @_seed_counter_]
    (swap! _seed_counter_ inc) n))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol ComObj
  "A Component"
  (co-hurt! [_ damage from] "")
  (co-inflate [_ options] "")
  (co-deflate [_] "")
  (co-width [_] "")
  (co-height [_] "")
  (co-pos [_] "")
  (co-pid [_] "")
  (co-size [_] "")
  (co-setname! [_ n] "")
  (co-setpos! [_ p] "")
  (co-setxy! [_ x y] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Component [state]
  ComObj
  (co-hurt! [_ damage from]
    (when (number? damage)
      (update-in state [:health] - damage)))
  (co-inflate [_ options]
    (when-some [s (:sprite @state)]
      (if (and (contains? options :x)
               (contains? options :y))
        (.setPosition s (:x options)(:y options)))
      (if (contains? options :deg)
        (.setRotation s (:deg options)))
      (if (contains? options :scale)
        (.setScale s (:scale options)))
      (.setVisible s true))
    (swap! state
           merge
           {:status true
            :origHealth (:health @state)}))
  (co-deflate [_]
    (when-some [s (:sprite @state)]
      (.unscheduleAllCallbacks s)
      (.stopAllActions s)
      (.setVisible s))
    (swap! state assoc :status false))
  (co-height [_]
    (when-some [s (:sprite @state)]
      (.. s getContentSize height)))
  (co-width [_]
    (when-some [s (:sprite @state)]
      (.. s getContentSize width)))
  (co-pos [_]
    (when-some [s (:sprite @state)]
      (.getPosition s)))
  (co-pid [_]
    (when-some [s (:sprite @state)]
      (.getTag s)))
  (co-size [_]
    (when-some [s (:sprite @state)]
      (.getContentSize s)))
  (co-setname! [_ n]
    (if (string? n)
      (swap! state assoc :name n)))
  (co-setpos! [_ p]
    (when-some [s (:sprite @state)]
      (.setPosition s p)))
  (co-setxy! [_ x y]
    (when-some [s (:sprite @state)]
      (.setPosition s x y))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn comObj<>
  ""
  ([sprite] (comObj<> sprite nil))
  ([sprite options]
   (->> (merge {:health 1
                :name (cs/join ":" ["co" (next-seed)])}
               options
               {:sprite sprite :status false})
        (Component. ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn l10nInit "" [table]
  (lz/setLocale! (-> js/cc (.-sys) (.-language)))
  (lz/setLocales table)
  (log/info "Loaded l10n strings.  locale = " lz/*current-locale*))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn l10n "" [s & pms]
  (let [t (lz/localize s)]
    (if (not-empty pms)
      (.render js/Mustache t (clj->js pms)) t)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn fire! "" [topic msg]
  (some-> (-> js/cc
              (.-director)
              (.getRunningScene))
          (.-ebus) (.fire topic (clj->js (or msg {})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLevelCfg "" [cfgObj level]
  (get-in cfgObj [:levels (str level) "cfg"]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn intersect? "" [a1 a2]
  (not (or (> (:left a1) (:right a2))
           (> (:left a2) (:right a1))
           (< (:top a1) (:bottom a2))
           (< (:top a2) (:bottom a1)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn outOfBound? "" [a B]
  (if (and a B)
      (or (> (:left a) (:right B))
          (< (:top a) (:bottom B))
          (< (:right a) (:left B))
          (> (:bottom a) (:top B)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn degToRad "" [deg]
  (/ (* deg (.-PI js/Math)) 180))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn radToDeg "" [rad]
  (/ (* 180 rad) (.-PI js/Math)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn calcXY
  "quadrants =  2 | 1
               --------
                3 | 4"
  [angle hypot]
  (let [angle (mod angle 360)
        m js/Math
        [x y q]
        (cond
          (and (>= angle 0) (<= angle 90))
          (let [t (degToRad (- 90 angle))]
            [(.cos m t) (.sin m t) 1])
          (and (>= angle 90) (<= angle 180 ))
          (let [t (degToRad (- angle 90))]
            [(.cos m t) (- (.sin m t)) 2])
          (and (>= angle 180) (<= angle 270))
          (let [t (degToRad (- 270 angle))]
            [(- (.cos m t)) (- (.sin m t)) 3])
          (and (>= angle 270) (<= angle 360))
          (let [t (degToRad (- angle 270))]
            [(- (.cos m t) (.sin m t) 4)]))]
    [(* x hypot) (* y hypot) q]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn normalizeDeg "" [deg] (mod deg 360))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getSfx "" [x])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn toggleSfx! ""
  ([] (toggleSfx! nil))
  ([value]
   (let [cv (get-in @*config* [:sound :open?])
         cv (or value (not cv))]
     (swap! *config*
            update-in [:sound] assoc :open cv))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn sfxMusicVol "" [vol]
  (if (and (number? vol)
           (get-in @*config*
                   [:sound :open?]))
    (-> js/cc
        (.-audioEngine)
        (.setMusicVolume vol))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn sfxPlayMusic "" [kee args]
  (when (get-in @*config*
                [:sound :open?])
    (sfxMusicVol (:volume args))
    (-> js/cc
        (.-audioEngine)
        (.playMusic (getSfx kee)
                    (true? (:repeat? args))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn sfxPlay "" [kee args]
  (when (get-in @*config*
                [:sound :open?])
    (sfxMusicVol (:volume args))
    (-> js/cc
        (.-audioEngine)
        (.playEffect (getSfx kee)
                     (true? (:repeat? args))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn sfxCancel "" []
  (doto (.-audioEngine js/cc) (.stopAllEffects) (.stopMusic)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn sfxInit "" []
  (swap! *config* update-in [:sound] assoc :open? true)
  (sfxMusicVol (get-in @*config* [:sound :volume])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn sanitizeUrlForDevice "" [url] )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn sanitizeUrlForWeb "" [url] )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn sanitizeUrl "" [url]
  (.log js/cc (str "About to sanitize url: " url))
  (let [rc
        (if (-> js/cc (.-sys) (.isNative))
          (sanitizeUrlForDevice url)
          (sanitizeUrlForWeb url))]
    (.log js/cc (str "Sanitize url: " rc))
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn throttle "" [func waitMillis options]
  (let
    [trailing? (not (false? (:trailing? options)))
     notleading? (false? (:leading? options))
     tout (atom 0)
     prev (atom 0)
     res (atom nil)
     ctx (atom nil)
     args (atom [])
     later
     (fn []
       (reset! prev
               (if notleading? 0 (now-millis)))
       (reset! tout 0)
       (reset! res (apply func @ctx @args))
       (when (= 0 @tout)
         (reset! ctx nil)
         (reset! args [])))]
    (fn [& xs]
      (let
        [now (now-millis)
         _ (if (and (= 0 @prev)
                    notleading?)
             (reset! prev now))
         remain (- waitMillis (- now @prev))]
        (reset! args xs)
        (reset! ctx (first xs))
        (cond
          (or (<= remain 0)
              (> remain waitMillis))
          (do
            (js/clearTimeout @tout)
            (reset! tout 0)
            (reset! prev now)
            (reset! res (apply func @ctx @args))
            (when (= 0 @tout)
              (reset! ctx nil)
              (reset! args [])))
          (and (= 0 @tout) trailing?)
          (reset! tout
                  (js/setTimeout later remain)))
        @res))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

