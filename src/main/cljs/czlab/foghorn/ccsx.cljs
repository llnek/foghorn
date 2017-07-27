;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.foghorn.ccsx)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pointInBox? ""
  ([box pt] (pointInBox? box (:x pt) (:y pt)))
  ([box x y]
   (and (>= x (:left box))
        (<= x (:right box))
        (>= y (:bottom box))
        (<= y (:top box)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn collide? "" [a b]
  (if (and a b)
    (collide0 (:sprite a) (:sprite b))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn collide0 "" [spriteA spriteB]
  (if (and spriteA spriteB)
    (.rectIntersectsRect js/cc
                         (bbox spriteA)
                         (bbox spriteB))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn setDevRes "" [landscape? w h pcy]
  (let [[a b] (if landscape? [w h] [h w])]
    (-> js/cc
        (.-view)
        (.setDesignResolutionSize a b pcy))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


