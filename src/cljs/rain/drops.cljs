(ns rain.drops
  (:require [rain.util :refer (pi tau log radius area square)]))

(def k-gravity 0.005)

(defn make-drop
  "return a new raindrop instance"
  [v]
  {:x (rand-int js/innerWidth)
   :y (rand-int js/innerHeight)
   :r (+ 2 (rand-int 6))})

(defn apply-gravity
  "calculate new position and acceleration of raindrop"
  [{:keys [x y r] :as drop}]
  (let [g (or (:g drop) k-gravity)
        dg (* k-gravity r)]
    (assoc drop
      :y (+ y g)
      :g (+ g dg))))

(defn merge-drops
  "return a new raindrop which has the average position of the 2 parents and the combined size"
  [d1 d2]
  {:x (/ (+ (:x d1) (:x d2)) 2)
   :y (/ (+ (:y d1) (:y d2)) 2)
   :r (radius (+ (area (:r d1)) (area (:r d2))))
   :g (/ (+ (or (:g d1) 0)
            (or (:g d2) 0))
         2)})

(defn overlapping?
  "Checks for whether 2 drops overlap.
Stolen from http://stackoverflow.com/questions/8367512/algorithm-to-detect-if-a-circles-intersect-with-any-other-circle-in-the-same-pla"
  [{x1 :x y1 :y r1 :r} {x2 :x y2 :y r2 :r}]

  (<= (square (- r1 r2))
      (+ (square (- x1 x2)) (square (- y1 y2)))
      (square (+ r1 r2))))

(defn merge-overlapping
  "Assumes there is at most one drop from drops, which overlaps drop.
Returns a new collection with overlapping values merged"
  [drops drop]
  (loop [d (first drops) ds (next drops) res []]
    (if d
      (if (overlapping? drop d)
        (concat (conj res (merge-drops drop d)) ds)
        (recur (first ds) (next ds) (conj res d)))
      (conj res drop))))


(comment
  (let [d1 {:x 10 :y 10 :r 5}
        d2 {:x 10 :y 10 :r 5}
        drops [{:x 10 :y 10 :r 5}
               {:x 100 :y 10 :r 5}
               {:x 1000 :y 10 :r 5}
               {:x 10000 :y 10 :r 5}
               {:x 100000 :y 10 :r 5}]
        drop {:x 11 :y 10 :r 4}]
    (doall (map log (map (partial overlapping? drop) drops)))
    (doall (map log (map :x (merge-overlapping drops drop))))))
