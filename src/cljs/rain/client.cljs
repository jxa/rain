(ns rain.client
  (:require
   [dommy.utils :as utils]
   [dommy.core :as dommy]
   [rain.util :refer (pi tau log)]
   [rain.drops :refer (make-drop apply-gravity merge-drops overlapping? merge-overlapping)]
   [rain.async :refer (timer-chan chunked)]
   [cljs.core.async :refer (<! >! chan map< filter< dropping-buffer)])
  (:use-macros
   [dommy.macros :only [node sel sel1]]
   [cljs.core.async.macros :only [go go-loop]]))

(def fps 25)
(def max-drops 1000)

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
        width (.-width bg)
        height (.-height bg)]
    (.drawImage (.getContext bg "2d") bg-image 0 0 width height)
    (js/stackBlurCanvasRGB "outside" 0 0 width height blur)
    bg))

(defn prepare-reflection [canvas bg-image]
  (let [reflection (prepare-canvas canvas)
        width (.-width reflection)
        height (.-height reflection)]
    (.drawImage (.getContext reflection "2d") bg-image 0 0 width height)
    reflection))

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

(defn on-screen?
  "returns true if raindrop is partially within bounds of window"
  [{:keys [x y r] :as drop}]
  (< (- y r) js/innerHeight))

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
        drops           (chan (dropping-buffer max-drops))
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

(.addEventListener js/document "DOMContentLoaded" init)
