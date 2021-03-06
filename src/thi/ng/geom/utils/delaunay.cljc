(ns thi.ng.geom.utils.delaunay
  #?(:cljs (:require-macros [thi.ng.math.macros :as mm]))
  (:require
   [thi.ng.geom.core :as g]
   [thi.ng.geom.triangle :refer [circumcircle-raw triangle2]]
   [thi.ng.math.core :as m :refer [*eps* delta=]]
   #?(:clj [thi.ng.math.macros :as mm])))

(defn- add-unique-edge!
  [edges p q]
  (let [e [p q]]
    (if (edges e)
      (disj! edges e)
      (let [e2 [q p]]
        (if (edges e2) (disj! edges e2) (conj! edges e))))))

(defn- compute-edges
  [complete tris [px py]]
  (persistent!
   (reduce
    (fn [state t]
      (if (complete t) state
          (let [x (- px (t 3))
                y (- py (t 4))]
            (if (<= (mm/madd x x y y) (t 5))
              (assoc! state
                      0 (let [[a b c] t]
                          (-> (state 0)
                              (add-unique-edge! a b)
                              (add-unique-edge! b c)
                              (add-unique-edge! c a))))
              (assoc! state
                      1 (conj! (state 1) t))))))
    (transient [(transient #{}) (transient [])])
    tris)))

(defn- triangle-spec
  [a b c]
  (let [[[ox oy] r] (circumcircle-raw a b c)]
    [a b c ox oy (* r r) (+ ox r)]))

(defn- shared-vertex?
  [a1 b1 c1 [a2 b2 c2]]
  (or (identical? a1 a2) (identical? a1 b2) (identical? a1 c2)
      (identical? b1 a2) (identical? b1 b2) (identical? b1 c2)
      (identical? c1 a2) (identical? c1 b2) (identical? c1 c2)))

;; Algorithm fails if *all* points are arranged in a circle. A
;; workaround is to add additional point(s) in the center.
;;
;; See: http://astronomy.swin.edu.au/~pbourke/modelling/triangulate/

(defn triangulate
  [points]
  (let [points (sort-by #(% 0) points)
        bmin (reduce m/min points)
        bmax (reduce m/max points)
        bext (m/- bmax bmin)
        dm (max (bext 0) (bext 1))
        d2 (* 2.0 dm)
        m (m/mix bmin bmax)
        [sa sb sc :as s] (triangle-spec (m/- m d2 dm) (m/+ m 0 d2) (m/+ m d2 (- dm)))]
    (loop [points points, tris [s], complete (transient #{})]
      (if-let [[px :as p] (first points)]
        (let [complete (reduce #(if (< (%2 6) px) (conj! % %2) %) complete tris)
              [edges tris] (compute-edges complete tris p)
              tris (reduce #(conj! % (triangle-spec (%2 0) (%2 1) p)) tris (persistent! edges))]
          (recur (rest points) (persistent! tris) complete))
        (->> tris
             (reduce conj! complete)
             (persistent!)
             (remove #(shared-vertex? sa sb sc %))
             (map (fn [t] [(t 1) (t 0) (t 2)])))))))
