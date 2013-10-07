(ns rain.async
  (:require [cljs.core.async :as async :refer (<! >! timeout chan alts! close!)])
  (:use-macros [cljs.core.async.macros :only [go go-loop]]))

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
