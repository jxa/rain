# Making it Rain

Drawing raindrops on html5 canvas with clojurescript and core.async, an experience report with code and demo.

I've been looking for a good excuse to learn more about clojurescript. I've also been following [@swannodette's](http://swannodette.github.io/) work with core.async. When a coworker of mine shared a link to the [rainyday demo](http://maroslaw.github.io/rainyday.js/demo1.html) I saw the opportunity to combine my two interests by trying to recreate the animation.

First check out http://jxa.github.io/rain/ to see some drops hit your browser.

## ClojureScript

For this project ClojureScript served me very well. There were very few places where I actually needed to do JavaScript interop. These I was able to isolate into the rain.client namespace. In order to focus on the animation I decided to use the original JS implementation of the blur algorithm. It might be fun to try a clojurescript version in the future, but I don't see any particular advantage at this point.

As an experienced JS programmer I was very happy with the ability to rely on real namespacing in order to separate concerns. Many annoying JS ceremonies are simply not required in ClojureScript. Module creation style, var creation and preventing global object pollution, encapsulating private functions, how exactly 'this' works, how to properly accomplish prototype inheritance; these are a few of the concepts you don't need to concern yourself with in cljs. 

Finally, because ClojureScript is a lisp, it is possible to write macros. I didn't write any macros for this project but I did use core.async which uses them extensively to rewrite asynchronous code so that you can write code that looks sequential.

## Core.Async

I don't want to give an introduction to core.async here. Others have done a [far](http://clojure.com/blog/2013/06/28/clojure-core-async-channels.html) [better](http://g33ktalk.com/core-async-a-clojure-library/) [job](http://swannodette.github.io/2013/07/12/communicating-sequential-processes/) than I would. Instead I'd like to talk about how it helped me to accomplish my objective.

I'm really impressed by how core.async helped me to decouple the rendering logic from the animation loop. For example, in the original javascript version, there is a raindrop object which keeps track of a drop's position and velocity. It also must keep a reference to the main application object in order to determine whether the drop was still within the canvas boundaries. Then when it determines that it has gone off screen it must clear its own interval timer so that it stops trying to draw itself.

By contrast, in the core.async version the drop is also an object (Map) but there is no logic built-in. There is a channel which takes care of filtering out the drops which are no longer on screen. 

```
(let [drops           (chan (dropping-buffer max-drops))
      animating-drops (filter< on-screen? drops)]
	...      
)
```

At first I started out by drawing each drop as it came through the channel. After it was drawn on the canvas I would set a timeout for the animation interval which would then enqueue the drop into the render queue once again. This worked fine for the first iteration.

The next feature I wanted to add was to try to merge droplets when they collide, which requires knowledge of all drop positions at the beginning of each render loop. Time to write some higher level async functions.

```
(defn take-for
  "returns a channel which reads from in until time-in-ms elapses"
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
  (let [...
        drops           (chan (dropping-buffer max-drops))
        animating-drops (filter< on-screen? drops)
        animation-tick  (chunked animating-drops (/ 1000 fps))]

	...

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

Pretty damn concise. Merging drops just becomes a reduction over `drops-to-animate`.

 I can't wait to find the next application of core.async.

## Running it locally

```lein ring server```

This should launch a web server on localhost:3000 and open a browser window.
If you want to modify the clojurescript source, open another terminal window and type
```lein cljsbuild auto```. This will recompile the JS every time you modify one of the cljs files.


## TODO

* I focused more on the animation logic than on the droplet style. Raindrops on glass are not perfect circles.
* Add reflection and/or transparency to drops
* Add droplet trails
