(ns kaocha.report.diff-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [kaocha.report.diff :as diff]))

(defmacro ^{:style/indent [1]} context [& args] `(testing ~@args))

(deftest diff-test
  (testing "diffing atoms"
    (context "nil"
      (is (= (diff/->Mismatch nil 1)
             (diff/diff nil 1))))

    (context "when different"
      (is (= (diff/->Mismatch :a :b)
             (diff/diff :a :b))))

    (context "when equal"
      (is (= :a
             (diff/diff :a :a)))))

  (testing "diffing collections"
    (context "different types"
      (is (= (diff/->Mismatch [1 2 3] #{1 2 3})
             (diff/diff [1 2 3] #{1 2 3}))))

    (context "sequences"
      (is (= []
             (diff/diff [] [])))

      (is (= [1 2 3]
             (diff/diff (into-array [1 2 3]) [1 2 3])))

      (is (= [:a]
             (diff/diff [:a] [:a])))

      (is (= [:a (diff/->Deletion :b) :c (diff/->Insertion :d)]
             (diff/diff [:a :b :c] [:a :c :d])))

      (is (= [:a (diff/->Deletion :b) :c (diff/->Insertion :d)]
             (diff/diff (list :a :b :c) (list :a :c :d))))

      (is (= [(diff/->Insertion :a)]
             (diff/diff [] [:a])))

      (is (= [(diff/->Deletion :a)]
             (diff/diff [:a] [])))

      (is (= [(diff/->Insertion :x) (diff/->Insertion :y) :a]
             (diff/diff [:a] [:x :y :a])))

      (is (= [:a (diff/->Mismatch :b :x) (diff/->Insertion :y) :c]
             (diff/diff [:a :b :c] [:a :x :y :c])))

      (is (= [{:x (diff/->Mismatch 1 2)}]
             (diff/diff [{:x 1}] [{:x 2}])))

      (is (= [(diff/->Insertion []) {} (diff/->Insertion [])]
             (diff/diff [{}]  [[] {} []])))

      (is (= [0 (diff/->Deletion 1) (diff/->Mismatch 2 :x) (diff/->Insertion :y) (diff/->Insertion :z)]
             (diff/diff [0 1 2] [0 :x :y :z]))))

    (context "sets"
      (is (= #{:a}
             (diff/diff #{:a} #{:a})))

      (is (= #{(diff/->Insertion :a)}
             (diff/diff #{} #{:a})))

      (is (= #{(diff/->Deletion :a)}
             (diff/diff #{:a} #{})))

      (is (= #{(diff/->Deletion :a) :b :c}
             (diff/diff #{:a :b :c} #{:c :b}))))

    (context "maps"
      (is (= {} (diff/diff {} {})))

      (is (= {:a (diff/->Mismatch 1 2)}
             (diff/diff {:a 1} {:a 2})))

      (is (= {:a (diff/->Mismatch 1 2)
              (diff/->Deletion :b) 2
              (diff/->Insertion :x) 2
              :c 3}
             (diff/diff {:a 1 :b 2 :c 3} {:a 2 :x 2 :c 3})))

      (is (= {:a [1 (diff/->Deletion 2) 3]}
             (diff/diff {:a [1 2 3]} {:a [1 3]}))))))

(deftest undiff-test
  (is (= {:a [1 2 [9 8 7]] :b 2 :c 3}
         (diff/left-undiff
          (diff/diff {:a [1 2 [9 8 7]] :b 2 :c 3}
                     {:a [1 3 [9 8]] :x 2 :c 3})))))

(deftest replacements-test
  (is (= [{} #{} {}]
         (diff/replacements [#{} {}])))

  (is (= [{1 :x} #{} {}]
         (diff/replacements [#{1} {1 [:x]}])))

  (is (= [{2 :x} #{} {}]
         (diff/replacements [#{2} {1 [:x]}])))

  (is (= [#{1 2} {2 [:x :y :z]}]
         (diff/del+ins [0 1 2] [0 :x :y :z])))

  (is (= [{2 :x} #{1} {2 '(:y :z)}]
         (diff/replacements [#{1 2} {2 [:x :y :z]}]))))

(defspec round-trip-diff 100
  (prop/for-all [x gen/any
                 y gen/any]
                (let [diff (diff/diff x y)]
                  (= [x y] [(diff/left-undiff diff) (diff/right-undiff diff)]))))


(comment
  (use 'kaocha.repl)
  (run)

  ;; REPL stuff to be converted to tests
  (diff/diff-seq [2]  [1 2 3])

  (diff/del+ins [2]  [1 2 3])

  (diff/del+ins [2 4]  [1 2 3 4 5])

  (apply diff/replacements (diff/del+ins [2 4]  [1 2 3 4 5]))

  (diff/diff-seq [2 4]  [1 2 3 4 5])

  (diff/del+ins [true 0] [-1 1 true])
  ;; => [#{1} {-1 [-1 1]}]
  ;; => [#{1} {-1 [-1 1]}]

  (diff/diff-seq [true 0] [-1 1 true])

  (diff/diff-seq-replacements {1 3} [1 2 3 4])

  (diff/diff-seq-deletions #{1} [1 2 3 4])

  (diff/diff-seq-insertions {1 [:x :y]} [1 2 3 4])

  (concat (take [1 2 3 4] 1) [:x :y] (drop ))

  (apply diff/replacements
         (diff/del+ins [true 0] [-1 1 true 4 5]))
  ;; => [{1 4} #{} {-1 [-1 1], 3 (5)}]

  (diff/right-undiff
   (diff/diff-seq [true 0] [-1 1 true 4 5]))
  ;; => ({:+ -1} {:+ 1} true {:- 0, :+ 4} {:+ 5})

  (->> [true 0]
       (diff/diff-seq-replacements {1 4})
       ;; => (true {:- 0, :+ 4})
       (diff/diff-seq-deletions #{})
       ;; => (true {:- 0, :+ 4})
       (diff/diff-seq-insertions {-1 [-1 1] 3 [5]})
       )
  ;; => ({:+ -1} {:+ 1} true {:- 0, :+ 4} {:+ 5})

  [#{1} {-1 [-1 1], 1 [4 5]}]

  (apply diff/replacements'
         (diff/del+ins [true 0] [-1 1 true]))


  (apply diff/replacements
         (diff/del+ins [true 0] [-1 1 true]))

  (diff/diff-seq [true 0] [-1 1 true])

  (diff/right-undiff
   (diff/diff-seq [true 0] [-1 1 true]))

  (-> (diff/del+ins [:a :b :c] [:a :c :d])
      (diff/replacements))
  ;; => [{} #{1} {1 [:d]}]
  ;; => [{} #{1} {2 [:d]}]
  (diff/diff-seq [:a :b :c] [:a :c :d])
  ;; => (:a :c {:+ :d})
  ;; => (:a :c {:+ :d})

  (diff/diff [{:x 1}] [{:x 2}])

  (diff/replacements (diff/del+ins [{:x 1}] [{:x 2}]))
  ;; => [{0 {:x 2}} #{} {-1 nil}]
  (diff/replacements (diff/del+ins [:a {:x 1}] [:a {:x 2}]))
  ;; => [{1 {:x 2}} #{1} {0 nil}]

  (diff/diff [0 0] [[[]] []])

  (-> (diff/del+ins [0 0] [[[]] []])
      ;; => [#{0 1} {-1 [[[]] []]}]

      diff/replacements
      ;; => [{0 [[]]} #{1} {-1 ([])}]
      )
  ;; => [{0 [[]]} #{1} {-1 ([])}]
  (-> (diff/del+ins [1 2] [:x :y])
      ;; => [#{0 1} {-1 [:x :y]}]
      diff/replacements
      ;; => [{0 :x} #{1} {-1 (:y)}]
      )

  (-> (diff/del+ins [1 2] [1 :x :y])
      ;; => [#{1} {1 [:x :y]}]
      diff/replacements
      ;; => [{0 :x} #{1} {1 (:y)}]
      )

  (->> [1 2]
       (diff/diff-seq-replacements {0 :x})
       ;; => (true {:- 0, :+ 4})
       (diff/diff-seq-deletions #{1})
       ;; => (true {:- 0, :+ 4})
       (diff/diff-seq-insertions {-1 [:y]})
       )
  ;; => ({:+ :y} {:- 1, :+ :x} {:- 2})
  ;; => ({:- 1, :+ :x} {:- 2})
  ;; => ({:- 1, :+ :x} 2)

  (diff/diff-seq [1 2] [1 :x :y])

  ;; => (1 {:- 2, :+ :x} {:+ :y})

  (diff/diff [0 0] [[]])

  (diff/del+ins [0 0] [[]])
  ;; => [#{0 1} {-1 [[]]}]

  (diff/replacements (diff/del+ins [0 0] [[]]))
  ;; => [{0 []} #{1} {0 nil}]
  ;; => [{0 [], 1 nil} #{} {1 ()}]

  (diff/replacements (diff/del+ins [0] [false]))
  ;; => [{0 false} #{} {0 nil}]

  (diff/diff [0] [false])

  (->> [0]
       (diff/diff-seq-replacements {0 false}))
  )
