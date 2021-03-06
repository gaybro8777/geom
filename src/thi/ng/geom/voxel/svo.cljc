(ns thi.ng.geom.voxel.svo
  (:require
   [thi.ng.math.core :as m]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.vector :as v :refer [vec3 V3]])
  #?(:clj (:import [thi.ng.geom.vector Vec3])))

;; Constants

(def depth-size
  (mapv #(long (Math/pow 8 %)) (range 16)))

(def depth-index
  (vec (reductions + 0 depth-size)))

(def bit-vals [0x01 0x02 0x04 0x08 0x10 0x20 0x40 0x80])

(def used-bits
  "Returns a lazy-seq of bit ids used in `x`.
  Only checks 8bit range (0 - 255)."
  (let [xf (fn [x] (vec (eduction (filter #(pos? (bit-and x (bit-vals %)))) (range 8))))]
    (into [] (map xf) (range 0x100))))

;; Helper & index functions

(defn node-id
  (^long
   [[ox oy oz] dim [px py pz]]
   (if (< pz (+ oz dim))
     (if (< px (+ ox dim))
       (if (< py (+ oy dim)) 0 1)
       (if (< py (+ oy dim)) 2 3))
     (if (< px (+ ox dim))
       (if (< py (+ oy dim)) 4 5)
       (if (< py (+ oy dim)) 6 7)))))

(defn node-bitmask
  (^long [o dim p] (bit-shift-left 1 (node-id o dim p))))

(defn node-offset
  "Computes the position of a child node id"
  (^thi.ng.geom.vector.Vec3
   [[x y z :as p] d]
   (let [[x' y' z'] (m/+ p d)]
     (fn [b]
       (vec3
        (if (> (bit-and b 2) 0) x' x)
        (if (> (bit-and b 1) 0) y' y)
        (if (> (bit-and b 4) 0) z' z)))))
  (^thi.ng.geom.vector.Vec3
   [[x y z] d b]
   (vec3
    (if (> (bit-and b 2) 0) (+ x d) x)
    (if (> (bit-and b 1) 0) (+ y d) y)
    (if (> (bit-and b 4) 0) (+ z d) z))))

(defn node-index
  (^long
   [^long idx ^long id ^long d sizes]
   (+ (inc idx) (* id (sizes d)))))

(defn max-depth
  "Returns max tree depth for the given size and min requested precision (voxel size)."
  (^long
   [dim prec]
   (loop [d dim, depth 0]
     (if (<= d prec)
       (dec depth)
       (recur (* d 0.5) (inc depth))))))

(defn size-at-depth
  "Returns node size at depth for given tree bounds."
  (^double [^double dim ^long depth] (/ dim (bit-shift-left 1 depth))))

;; SVO implementation & operations

(defrecord SVO [data dim max-depth prec branches])

(defn voxeltree
  "Creates a new voxel tree structure with the given root
  node dimension and min precision."
  [^double dim prec]
  (let [depth (max-depth dim prec)]
    (map->SVO
     {:data      {}
      :dim       dim
      :max-depth depth
      :prec      (size-at-depth dim (inc depth))
      :branches  (vec (cons 0 (reverse (map #(apply + (take % depth-size)) (range 1 (inc depth))))))})))

(defn set-at
  "Marks voxel at given position as set. Updates tree at all levels."
  ([tree v]
   (set-at tree V3 (* (get tree :dim) 0.5) 0 0 (get tree :max-depth) (get tree :branches) v))
  ([tree offset dim idx depth max-depth branches v]
   (let [id   (node-id offset dim v)
         data (get tree :data)
         tree (assoc tree :data
                     (assoc data idx
                            (bit-or (get data idx 0) (bit-shift-left 1 id))))]
     ;; (prn :d depth :o offset :o2 (m/madd (vec3 dim) 2 offset) :dim dim :idx idx :val (get-in tree [:data idx]))
     (if (< depth max-depth)
       (recur tree
              (node-offset offset dim id)
              (* dim 0.5)
              (node-index idx id (inc depth) branches)
              (inc depth)
              max-depth
              branches
              v)
       tree))))

(defn depth-at
  "Returns max defined tree depth at given position."
  ([tree v]
   (depth-at tree V3 (* (get tree :dim) 0.5) 0 0 (get tree :max-depth) (get tree :branches) v))
  ([tree max-depth v]
   (depth-at tree V3 (* (get tree :dim) 0.5) 0 0 (min max-depth (get tree :max-depth)) (get tree :branches) v))
  ([tree offset dim idx depth max-depth branches v]
   (let [id     (node-id offset dim v)
         n-val  (get (get tree :data) idx 0)
         found? (and (> n-val 0) (> (bit-and n-val (bit-shift-left 1 id)) 0))]
     ;; (prn :d depth :o offset :o2 (m/madd (vec3 dim) 2 offset) :dim dim :idx idx :id id :val n-val :found found?)
     (if found?
       (if (< depth max-depth)
         (recur tree
                (node-offset offset dim id)
                (* dim 0.5)
                (node-index idx id (inc depth) branches)
                (inc depth)
                max-depth
                branches
                v)
         depth)
       depth))))

(defn delete-at
  ([tree v]
   (first (delete-at tree V3 (* (get tree :dim) 0.5) 0 0 (get tree :max-depth) (get tree :branches) v)))
  ([tree offset dim idx depth max-depth branches v]
   (let [id    (node-id offset dim v)
         bmask (bit-shift-left 1 id)
         n-val (get (get tree :data) idx 0)]
     (if (pos? (bit-and n-val bmask))
       (if (< depth max-depth)
         (let [c-depth (inc depth)
               c-idx (node-index idx id c-depth branches)
               [tree edit? :as result]
               (delete-at tree
                          (node-offset offset dim id)
                          (* dim 0.5)
                          c-idx
                          c-depth max-depth
                          branches v)
               new-val (bit-and n-val (bit-xor 0xff bmask))]
           (if (and edit? (zero? (get (get tree :data) c-idx 0)))
             (if (zero? new-val)
               [(assoc tree :data (dissoc (get tree :data) c-idx idx)) true]
               [(assoc tree :data (assoc (dissoc (get tree :data) c-idx) idx new-val)) true])
             result))
         (let [new-val (bit-and n-val (bit-xor 0xff bmask))]
           ;; (prn :d depth :o offset :o2 (m/madd (vec3 dim) 2 offset) :dim dim :idx idx :id id :val n-val)
           (if (== new-val n-val)
             [tree false]
             [(assoc tree :data (assoc (get tree :data) idx new-val)) true])))
       [tree false]))))

(defn select
  ([tree min-depth]
   (->> (transient #{})
        (select (get tree :data) V3 (* (get tree :dim) 0.5) 0 0 (min min-depth (get tree :max-depth)) (get tree :branches))
        (persistent!)))
  ([data offset dim idx depth min-depth branches acc]
   (let [n-val   (get data idx 0)
         c-depth (inc depth)
         c-dim   (* dim 0.5)
         noff    (node-offset offset dim)]
     ;; (prn :d depth :o offset :o2 (m/madd (vec3 dim) 2 offset) :dim dim :idx idx :val n-val)
     (if (zero? n-val)
       acc
       (if (< depth min-depth)
         (reduce
          (fn [acc id]
            (select data
                    (noff id)
                    c-dim
                    (node-index idx id c-depth branches)
                    c-depth
                    min-depth
                    branches
                    acc))
          acc (used-bits n-val))
         ;; collect voxels
         (reduce
          #(conj! % (m/+ (noff %2) c-dim))
          acc (used-bits n-val)))))))

(defn voxel-config-at-depth
  "Returns a map of configuration settings for the given `tree` and
  `depth`. Depth will be clamped at tree's max-depth."
  [{:keys [dim max-depth] :as tree} min-depth]
  (let [depth  (max (min min-depth max-depth) 0)
        s      (size-at-depth dim (inc depth))
        s2     (* s 0.5)
        stride (int (/ dim s))]
    {:depth    depth
     :size     s
     :inv-size (/ 1.0 s)
     :offset   (vec3 s2 s2 s2)
     :stride   stride
     :stride-z (* stride stride)}))

(defn voxel-cell
  "Returns the cell coordinate for the given `index` and tree
  configuration as vec3 (the latter obtained via
  `voxel-config-at-depth`)."
  (^thi.ng.geom.vector.Vec3
   [{:keys [size stride stride-z]} idx]
   (let [z (int (/ idx stride-z))
         y (int (/ (rem idx stride-z) stride))
         x (rem idx stride)]
     (vec3 x y z))))

(defn voxel-coord
  "Returns the actual world space coordinate for the given `index` and
  tree configuration (the latter obtained via
  `voxel-config-at-depth`)."
  (^thi.ng.geom.vector.Vec3
   [config ^long idx]
   (m/* (voxel-cell config idx) (get config :size))))

(defn cell-index
  "Returns the index for the cell at xyz with `stride` and `stride-z`."
  [stride stride-z x y z]
  (+ (+ x (* y stride)) (* z stride-z)))

(defn select-cells
  ([tree min-depth]
   (->> (transient #{})
        (select-cells
         (get tree :data) V3 (* (get tree :dim) 0.5) 0 0
         (voxel-config-at-depth tree min-depth) (get tree :branches))
        (persistent!)))
  ([data offset dim idx depth config branches acc]
   (let [n-val   (get data idx 0)
         c-depth (inc depth)
         c-dim   (* dim 0.5)
         noff    (node-offset offset dim)]
     ;; (prn :d depth :o offset :o2 (m/madd (vec3 dim) 2 offset) :dim dim :idx idx :val n-val)
     (if (zero? n-val)
       acc
       (if (< depth (get config :depth))
         (reduce
          (fn [acc id]
            (select-cells data
                          (noff id)
                          c-dim
                          (node-index idx id c-depth branches)
                          c-depth
                          config branches
                          acc))
          acc (used-bits n-val))
         ;; collect voxels
         (let [{:keys [inv-size stride stride-z]} config]
           (reduce
            (fn [acc c]
              (let [[x y z] (noff c)]
                (conj! acc (+ (+ (int (* x inv-size))
                                 (* (int (* y inv-size)) stride))
                              (* (int (* z inv-size)) stride-z)))))
            acc (used-bits n-val))))))))

(defn apply-voxels
  [f tree coll] (reduce f tree coll))

#?(:clj
   (defn as-array
     [tree min-depth]
     (let [{:keys [stride stride-z]} (voxel-config-at-depth tree min-depth)
           ^bytes buf (byte-array (* stride stride-z))
           v   (byte 127)]
       (doseq [c (select-cells tree min-depth)]
         (aset-byte buf (int c) v))
       buf)))
