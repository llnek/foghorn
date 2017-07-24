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
  ;(:require-macros [clojure.core :refer :all])
  (:require [clojure.string :as cs]))

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
  (.toLocaleString js/LZString table)
  (set! .-locale js/LZString cc.sys.language)
  (set! .-defaultLocale js/LZString "en"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

