(ns thi.ng.geom.test.types.utils
  #?(:cljs
     (:require-macros
      [cemerick.cljs.test :refer (is deftest with-test run-tests testing)]))
  (:require
   [thi.ng.geom.core :as g]
   [thi.ng.geom.vector :refer [vec2 vec3]]
   [thi.ng.geom.types]
   [thi.ng.geom.aabb :as a]
   [thi.ng.geom.circle :as c]
   [thi.ng.geom.rect :as r]
   [thi.ng.geom.sphere :as s]
   [thi.ng.geom.triangle :as t]
   [thi.ng.geom.utils :as gu]
   [thi.ng.math.core :as m]
   #?(:clj
      [clojure.test :refer :all]
      :cljs
      [cemerick.cljs.test])))

(defn bounds=
  [a b] (and (m/delta= (get a :p) (get b :p)) (m/delta= (get a :size) (get b :size))))

(deftest test-fit-into-aabb
  (let [b1 (a/aabb (vec3 10 20 30) (vec3 1))]
    (is (bounds=
         (a/aabb)
         (gu/coll-bounds (gu/fit-all-into-bounds (a/aabb) [(s/sphere -1 1) (a/aabb 2 1)])))
        "fit 1")
    (is (bounds=
         (a/aabb [0.5 1.5 0] [1 1 5])
         (gu/coll-bounds (gu/fit-all-into-bounds (a/aabb 2 4 5) [(s/sphere 1 1) (a/aabb [0 1 9] 1)])))
        "fit 2")
    (is (bounds=
         (a/aabb (vec3 10.25 20.25 30) (vec3 0.5 0.5 1))
         (gu/coll-bounds (gu/fit-all-into-bounds b1 [(t/triangle3 [[0 0 -1] [1 0 1] [1 1 0]])])))
        "fit 3")
    (is (bounds=
         b1
         (gu/coll-bounds (gu/fit-all-into-bounds b1 [(t/triangle3 [[-1 0 -1] [0 0 0] [0 1 0]])])))
        "fit 4")
    ))

(deftest test-fit-into-rect
  (let [b1 (r/rect (vec2 10 20) 1)]
    (is (bounds=
         (r/rect)
         (gu/coll-bounds (gu/fit-all-into-bounds (r/rect) [(c/circle (vec2 -1) 1) (r/rect (vec2 2) 1)])))
        "fit 1")
    (is (bounds=
         (r/rect (vec2 10.25 20) 0.5 1)
         (gu/coll-bounds (gu/fit-all-into-bounds b1 [(t/triangle2 [[0 -1] [1 0] [0 1]])])))
        "fit 2")
    (is (bounds=
         b1
         (gu/coll-bounds (gu/fit-all-into-bounds b1 [(t/triangle2 [[-1 0] [0 0] [0 1]])])))
        "fit 3")
    ))
