(ns rain.util)

(def pi (.-PI js/Math))
(def tau (* 2 pi))

(defn log [stuff]
  (.log js/console stuff))

(defn area [r]
  "area of a circle of radius r"
  (* pi r r))

(defn radius
  "given the area of a circle, calculate the radius"
  [area]
  (.sqrt js/Math (/ area pi)))

(defn square [x]
  (* x x))
