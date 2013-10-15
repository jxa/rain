# Rain

Drawing raindrops on html5 canvas with clojurescript and core.async. Concept and image stolen from http://maroslaw.github.io/rainyday.js/demo1.html

First check out http://jxa.github.io/rain/ to see some drops hit your browser.

## Core.Async

I'm really impressed by how core.async helped me to decouple the rendering logic from the animation loop. For example, in the original javascript version, there is a raindrop object which keeps track of a drop's position and velocity. It also must keep a reference to the main application object in order to determine whether the drop was still within the canvas boundaries. Then when it determines that it has gone off screen it must clear its own interval timer so that it stops trying to draw itself.

By contrast, in the core.async version the drop is also an object (Map) but there is no logic built-in. There is a channel which takes care of filtering out the drops which are no longer on screen. 

```
(let [drops           (chan (dropping-buffer max-drops))
      animating-drops (filter< on-screen? drops)]
	...      
)
```

At first I started out by drawing each drop as it came through the channel. After it was drawn on the canvas I would set a timeout for the animation interval which would then enqueue the drop into the render queue once again. I then decided to try to merge droplets when they collide, which requires knowledge of all drop positions at the beginning of each render loop. Core.Async to the rescue…

```
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
```

I was able to create a channel abstraction, `chunked` which emits a vector of drops every tick on the render clock (25 frames per second, currently). With drops vector in hand it's straightforward to merge overlapping drops.

```
  (let [bg              (draw-canvas (sel1 :#outside) (sel1 :#background))
        glass           (draw-canvas (sel1 :#glass))
        reflection      (draw-canvas (sel1 :#reflection) (sel1 :#background))
        new-drops       (map< make-drop (timer-chan #(* 20 (rand-int 10)) :drop))
        drops           (chan (dropping-buffer max-drops))
        animating-drops (filter< on-screen? drops)
        animation-tick  (chunked animating-drops (/ 1000 fps))]

    ;; generate new raindrops
    (go-loop []
             (>! drops (<! new-drops))
             (recur))

    ;; raindrop render loop
    (go-loop [drops-to-animate (<! animation-tick)]
             (doseq [drop drops-to-animate]
               (clear-drop glass drop))
               
             (doseq [drop (reduce merge-overlapping [] drops-to-animate)]
               (let [next-drop (apply-gravity drop)]
                 (draw-drop glass next-drop reflection)
                 (>! drops next-drop)))
                 
             (recur (<! animation-tick))))
```

Pretty damn concise. I can't wait to find the next application of core.async.

## Running it locally

```lein ring server```

This should launch a web server on localhost:3000 and open a browser window.
If you want to modify the clojurescript source, open another terminal window and type
```lein cljsbuild auto```


## TODO

* raindrops aren't really circles
* add reflection and/or transparency to drops
* performance could be better
