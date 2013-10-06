(ns rain.client
  (:require
   [dommy.utils :as utils]
   [dommy.core :as dommy]
   [cljs.core.async :as async :refer (<! >! timeout chan take! alts! map< filter<
                                         buffer dropping-buffer)])
  (:use-macros
   [dommy.macros :only [node sel sel1]]
   [cljs.core.async.macros :only [go go-loop]]))

(def pi (.-PI js/Math))
(def tau (* 2 pi))

(def fps 25)
(def k-gravity (/ (* 0.005 fps) 25))

(defn log [stuff]
  (.log js/console stuff))

(defn prepare-canvas [canvas]
  (let [width js/innerWidth
        height js/innerHeight]
    (aset canvas "width" width)
    (aset canvas "height" height)
    canvas))

(defn prepare-bg [canvas bg-image blur]
  (let [bg (prepare-canvas canvas)]
    (.drawImage (.getContext bg "2d")
                bg-image 0 0 (.-width bg) (.-height bg))
    (js/stackBlurCanvasRGB "outside" 0 0 (.-width bg) (.-height bg) blur)
    bg))

(defn prepare-reflection [canvas bg-image]
  (let [reflection (prepare-canvas canvas)
        width (.-width reflection)
        height (.-height reflection)]
    (doto (.getContext reflection "2d")
      (.drawImage bg-image 0 0 width height)
      (comment
        (.translate (/ width 2) (/ height 2))
        (.rotate pi)
        (.drawImage bg-image (- (/ width 2)) (- (/ height 2)) width height)))
    reflection))

(defn timer-chan
  "create a channel which emits a message every (delay-fn) milliseconds
   if the optional stop parameter is provided, any value written
   to stop will terminate the timer"
  ([delay-fn msg]
     (timer-chan delay-fn msg (chan)))
  ([delay-fn msg stop]
     (let [out (chan)]
       (go
        (loop []
          (when-not (= stop (second (alts! [stop (timeout (delay-fn))])))
            (>! out msg)
            (recur))))
       out)))

(defn delay-put!
  "put val onto port after delaying by ms"
  [port val ms]
  (js/setTimeout (fn [] (go (>! port val))) ms))

(defn make-drop [v]
  {:x (rand-int js/innerWidth)
   :y (rand-int js/innerHeight)
   :r (+ 2 (rand-int 6))})

(defn draw-drop
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
  [canvas {:keys [x y r] :as drop}]
  (.clearRect (.getContext canvas "2d")
              (dec (- x r))
              (dec (- y r))
              (+ 2 (* 2 r))
              (+ 2 (* 2 r))))

(defn apply-gravity [{:keys [x y r] :as drop}]
  (let [g (or (:g drop) k-gravity)
        dg (* k-gravity r)]
    (assoc drop
      :y (+ y g)
      :g (+ g dg))))

(defn on-screen? [{:keys [x y r] :as drop}]
  (< (- y r) js/innerHeight))

(defn init
  "The Canvases
- the background, with blur applied
- the glass, canvas for drawing the raindrops
- the reflection, canvas holding inverted image (unblurred) for drop reflection

The channels
- new drops, randomly placed by a random timer
- drops that need to be (re-)rendered
- filtered drops by whether they are still on screen
- animation loop
"
  []
  (let [bg              (prepare-bg (sel1 :#outside) (sel1 :#background) 15)
        glass           (prepare-canvas (sel1 :#glass))
        reflection      (prepare-reflection (sel1 :#reflection) (sel1 :#background))
        new-drops       (map< make-drop (timer-chan #(* 50 (rand-int 10)) :drop))
        drops           (chan (dropping-buffer 1000))
        animating-drops (filter< on-screen? drops)]

    (go-loop [drop (<! new-drops)]
        (draw-drop glass drop reflection)
        (delay-put! drops drop (/ 1000 fps))
        (recur (<! new-drops)))

    (go-loop [drop (<! animating-drops)]
        (let [next-drop (apply-gravity drop)]
          (clear-drop glass drop)
          (draw-drop glass next-drop reflection)
          (delay-put! drops next-drop (/ 1000 fps)))
        (recur (<! animating-drops)))))

(.addEventListener js/document "DOMContentLoaded" init)
