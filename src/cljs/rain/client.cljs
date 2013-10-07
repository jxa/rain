(ns rain.client
  (:require
   [dommy.utils :as utils]
   [dommy.core :as dommy]
   [cljs.core.async :as async :refer (<! >! timeout chan take! alts! map< filter<
                                         buffer dropping-buffer close!)])
  (:use-macros
   [dommy.macros :only [node sel sel1]]
   [cljs.core.async.macros :only [go go-loop]]))

(def pi (.-PI js/Math))
(def tau (* 2 pi))

(def fps 25)
(def k-gravity (/ (* 0.005 fps) 25))

(defn log [stuff]
  (.log js/console stuff))

(defn prepare-canvas
  "resize the canvas element to window height and width"
  [canvas]
  (let [width js/innerWidth
        height js/innerHeight]
    (aset canvas "width" width)
    (aset canvas "height" height)
    canvas))

(defn prepare-bg [canvas bg-image blur]
  (let [bg (prepare-canvas canvas)
        width (.-width reflection)
        height (.-height reflection)]
    (.drawImage (.getContext bg "2d") bg-image 0 0 width height)
    (js/stackBlurCanvasRGB "outside" 0 0 width height blur)
    bg))

(defn prepare-reflection [canvas bg-image]
  (let [reflection (prepare-canvas canvas)
        width (.-width reflection)
        height (.-height reflection)]
    (.drawImage (.getContext reflection "2d") bg-image 0 0 width height)
    reflection))

(defn timer-chan
  "create a channel which emits a message every (delay-fn) milliseconds
   if the optional stop parameter is provided, any value written
   to stop will terminate the timer"
  ([delay-fn msg]
     (timer-chan delay-fn msg (chan)))
  ([delay-fn msg stop]
     (let [out (chan)]
       (go-loop []
           (when-not (= stop (second (alts! [stop (timeout (delay-fn))])))
             (>! out msg)
             (recur)))
       out)))

(defn delay-put!
  "put val onto port after delaying by ms"
  [port val ms]
  (js/setTimeout (fn [] (go (>! port val))) ms))

(defn make-drop
  "return a new raindrop instance"
  [v]
  {:x (rand-int js/innerWidth)
   :y (rand-int js/innerHeight)
   :r (+ 2 (rand-int 6))})

(defn take-for
  "returns a channel which is open until either chan is closed or time-in-ms elapses"
  [in time-in-ms]
  (let [t (timeout time-in-ms)
        out (chan)]
    (go-loop [[msg c] (alts! [in t])]
      (if (= t c)
        (close! out)
        (do
          (>! out msg)
          (recur (alts! [in t])))))
    out))

(defn chunked
  "returns an output channel which emits one vector of values
for each unit of time"
  [in time-in-ms]
  (let [out (chan)]
    (go-loop []
             (>! out (<! (async/reduce conj [] (take-for in time-in-ms))))
             (recur))
    out))

(defn merge-drops
  "takes a vector of drops. returns a possibly smaller vector of drops that
are merged if they overlap"
  [drops]
  drops)

(defn draw-drop
  "draw a circle on canvas with given reflection"
  [canvas {:keys [x y r] :as drop} reflection]
  (doto (.getContext canvas "2d")
    (.save)
    (.beginPath)
    (.arc x y r 0 tau false)
    (.closePath)
    (.clip)
    (.drawImage reflection
                (- x (* r r)) (- y (* r r)) (* 4 r r) (* -4 r r)
                (- x r) (- y r) (* 2 r) (* 2 r))
    (.restore)))

(defn clear-drop
  "erase previously drawn raindrop"
  [canvas {:keys [x y r] :as drop}]
  (.clearRect (.getContext canvas "2d")
              (dec (- x r))
              (dec (- y r))
              (+ 2 (* 2 r))
              (+ 2 (* 2 r))))

(defn apply-gravity
  "calculate new position and acceleration of raindrop"
  [{:keys [x y r] :as drop}]
  (let [g (or (:g drop) k-gravity)
        dg (* k-gravity r)]
    (assoc drop
      :y (+ y g)
      :g (+ g dg))))

(defn on-screen?
  "returns true if raindrop is partially within bounds of window"
  [{:keys [x y r] :as drop}]
  (< (- y r) js/innerHeight))

(defn area [r]
  "area of a circle of radius r"
  (* pi r r))

(defn radius
  "given the area of a circle, calculate the radius"
  [area]
  (.sqrt js/Math (/ area pi)))

(defn merge-drops
  "return a new raindrop which has the average position of the 2 parents and the combined size"
  [d1 d2]
  {:x (/ (+ (:x d1) (:x d2)) 2)
   :y (/ (+ (:y d1) (:y d2)) 2)
   :r (radius (+ (area (:r d1)) (area (:r d2))))
   :g (/ (+ (or (:g d1) 0)
            (or (:g d2) 0))
         2)})

(defn square [x]
  (* x x))

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

(defn init
  "The Canvases
- the background, with blur applied
- the glass, canvas for drawing the raindrops
- the reflection, canvas holding inverted image (unblurred) for drop reflection

The channels
- new-drops: randomly placed by a random timer
- drops: need to be (re-)rendered
- animating-drops: filtered by whether they are still on screen
- animation-tick: collects the next animation loop's worth of drops
"
  []
  (let [bg              (prepare-bg (sel1 :#outside) (sel1 :#background) 15)
        glass           (prepare-canvas (sel1 :#glass))
        reflection      (prepare-reflection (sel1 :#reflection) (sel1 :#background))
        new-drops       (map< make-drop (timer-chan #(* 20 (rand-int 10)) :drop))
        drops           (chan (dropping-buffer 1000))
        animating-drops (filter< on-screen? drops)
        animation-tick  (chunked animating-drops (/ 1000 fps))]

    (go-loop []
             (>! drops (<! new-drops))
             (recur))

    (go-loop [drops-to-animate (<! animation-tick)]
             (doseq [drop drops-to-animate]
               (clear-drop glass drop))
             (doseq [drop (reduce merge-overlapping [] drops-to-animate)]
               (let [next-drop (apply-gravity drop)]
                 (draw-drop glass next-drop reflection)
                 (>! drops next-drop)))
             (recur (<! animation-tick)))))

(defn test []
  (let [d1 {:x 10 :y 10 :r 5}
        d2 {:x 10 :y 10 :r 5}
        drops [{:x 10 :y 10 :r 5}
               {:x 100 :y 10 :r 5}
               {:x 1000 :y 10 :r 5}
               {:x 10000 :y 10 :r 5}
               {:x 100000 :y 10 :r 5}]
        drop {:x 11 :y 10 :r 4}]
    (doall (map log (map (partial overlapping? drop) drops)))
    (doall (map log (map :x (merge-overlapping drops drop))))
    ))

(.addEventListener js/document "DOMContentLoaded" init)
