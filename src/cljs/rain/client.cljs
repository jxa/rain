(ns rain.client
  (:require
   [dommy.utils :as utils]
   [dommy.core :as dommy]
   [cljs.core.async :as async :refer (<! >! timeout chan take! alts! map<)])
  (:use-macros
   [dommy.macros :only [node sel sel1]]
   [cljs.core.async.macros :only [go go-loop]]))

(def pi (.-PI js/Math))
(def tau (* 2 pi))

(def fps 25)
(def k-gravity (/  (* 0.005 fps) 25))

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
      (.translate (/ width 2) (/ height 2))
      (.rotate pi)
      (.drawImage bg-image (- (/ width 2)) (- (/ height 2)) width height))
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

(defn draw-drop
"
context.beginPath();
      context.arc(centerX, centerY, radius, 0, 2 * Math.PI, false);
      context.fillStyle = 'green';
      context.fill();
      context.lineWidth = 5;
      context.strokeStyle = '#003300';
      context.stroke();
"
 [canvas {:keys [x y r] :as drop}]
 (doto (.getContext canvas "2d")
   (.beginPath)
   (.arc x y r 0 tau false)
   (aset "fillStyle" "white")
   (aset "lineWidth" 1)
   (aset "strokeStyle" "#000000")
   (.closePath)
   (.fill)
   (.stroke)))

(defn make-drop [v]
  {:x (rand-int js/innerWidth)
   :y (rand-int js/innerHeight)
   :size (+ 2 (rand-int 6))})

(defn apply-gravity [{:keys [x y r] :as drop}]
  (let [g (:gravity drop)]
    {:x x
     :y (+ y (or g k-gravity)) (* k-gravity)}))

(defn init []
  (let [bg (prepare-bg (sel1 :#outside) (sel1 :#background) 15)
        glass (prepare-canvas (sel1 :#glass))
        reflection (prepare-reflection (sel1 :#reflection) (sel1 :#background))
        timer (timer-chan #(* 100 (rand-int 10)) :hello)
        drops (map< make-drop timer)]
    (let []
      (go (loop []
            (draw-drop glass (<! drops))
            (recur))))))

(.addEventListener js/document "DOMContentLoaded" init)
