(ns crap4clj.complexity-spec
  (:require [speclj.core :refer :all]
            [crap4clj.complexity :refer :all]))

(describe "cyclomatic complexity"
  (context "base complexity"
    (it "returns 1 for an empty function"
      (should= 1 (cyclomatic-complexity "(defn foo [])")))

    (it "returns 1 for a function with no branches"
      (should= 1 (cyclomatic-complexity "(defn foo [x]\n  (+ x 1))"))))

  (context "if forms"
    (it "counts if as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x]\n  (if x 1 0))")))

    (it "counts if-not as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x]\n  (if-not x 1 0))")))

    (it "counts if-let as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x]\n  (if-let [y x] y 0))")))

    (it "counts if-some as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x]\n  (if-some [y x] y 0))"))))

  (context "when forms"
    (it "counts when as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x]\n  (when x 1))")))

    (it "counts when-not as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x]\n  (when-not x 1))")))

    (it "counts when-let as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x]\n  (when-let [y x] y))")))

    (it "counts when-some as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x]\n  (when-some [y x] y))")))

    (it "counts when-first as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x]\n  (when-first [y x] y))"))))

  (context "logical operators"
    (it "counts and as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x y]\n  (and x y))")))

    (it "counts or as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x y]\n  (or x y))"))))

  (context "loop"
    (it "counts loop as 1 decision point"
      (should= 2 (cyclomatic-complexity "(defn foo [x]\n  (loop [i 0] (recur (inc i))))"))))

  (context "catch"
    (it "counts each catch as 1 decision point"
      (should= 2 (cyclomatic-complexity
        "(defn foo []\n  (try (bar)\n    (catch Exception e nil)))")))

    (it "counts multiple catches"
      (should= 3 (cyclomatic-complexity
        "(defn foo []\n  (try (bar)\n    (catch Exception e nil)\n    (catch Error e nil)))"))))

  (context "cond forms"
    (it "counts each cond clause as a decision point"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (cond\n    (= x 1) :one\n    (= x 2) :two))")))

    (it "counts cond with :else"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (cond\n    (= x 1) :one\n    :else :other))"))))

  (context "condp forms"
    (it "counts each condp clause as a decision point"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (condp = x\n    1 :one\n    2 :two))"))))

  (context "case forms"
    (it "counts each case clause as a decision point"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (case x\n    1 :one\n    2 :two))")))

    (it "counts case with default"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (case x\n    1 :one\n    :other))"))))

  (context "map and vector literals in cond branches"
    (it "does not count map literal contents as separate forms"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (cond\n    (= x 1) {:type :army :mode :awake}\n    :else nil))")))

    (it "does not count vector literal contents as separate forms"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (cond\n    (= x 1) [:a :b :c]\n    :else nil))")))

    (it "handles nested maps in cond branches"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (cond\n    (= x 1) {:a {:b 1}}\n    :else nil))")))

    (it "handles mixed parens, maps, and vectors"
      (should= 6 (cyclomatic-complexity
        (str "(defn foo [cell]\n"
             "  (let [contents (:contents cell)]\n"
             "    (cond\n"
             "      (pred-a? contents)\n"
             "      {:type :army :mode :awake :owner (:owner contents) :aboard true}\n"
             "\n"
             "      (pred-b? contents)\n"
             "      {:type :fighter :mode :awake :owner (:owner contents) :fuel 20 :from-carrier true}\n"
             "\n"
             "      (pred-c? contents) contents\n"
             "\n"
             "      (pred-d? cell)\n"
             "      {:type :fighter :mode :awake :owner :player :fuel 20 :from-airport true}\n"
             "\n"
             "      :else nil))")))))

  (context "cond-> forms"
    (it "counts each cond-> clause as a decision point"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (cond-> x\n    (pos? x) inc\n    (even? x) (* 2)))")))

    (it "counts cond-> with single clause"
      (should= 2 (cyclomatic-complexity
        "(defn foo [x]\n  (cond-> x\n    true inc))")))

    (it "counts nested cond-> clauses"
      (should= 5 (cyclomatic-complexity
        "(defn foo [x]\n  (cond-> x\n    (pos? x) inc\n    (even? x) dec\n    (odd? x) (* 2)\n    (neg? x) abs))"))))

  (context "cond->> forms"
    (it "counts each cond->> clause as a decision point"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (cond->> x\n    (pos? x) (map inc)\n    (even? x) (filter odd?)))"))))

  (context "some-> forms"
    (it "counts each some-> step as a decision point"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (some-> x inc dec))")))

    (it "counts some-> with single step"
      (should= 2 (cyclomatic-complexity
        "(defn foo [x]\n  (some-> x inc))")))

    (it "counts some-> with form expressions"
      (should= 4 (cyclomatic-complexity
        "(defn foo [x]\n  (some-> x :name str clojure.string/upper-case))"))))

  (context "some->> forms"
    (it "counts each some->> step as a decision point"
      (should= 3 (cyclomatic-complexity
        "(defn foo [x]\n  (some->> x (map inc) (filter pos?)))"))))

  (context "combined decision points"
    (it "counts multiple decision points"
      (should= 5 (cyclomatic-complexity
        "(defn foo [x y]\n  (if x\n    (when y\n      (if (and x y) 1 0))\n    0))")))

    (it "ignores decision words in comments"
      (should= 1 (cyclomatic-complexity
        "(defn foo [x]\n  ;; if when cond\n  x)")))

    (it "ignores decision words in strings"
      (should= 1 (cyclomatic-complexity
        "(defn foo [x]\n  \"if when cond and or\"\n  x)")))

    (it "ignores decision forms in strings"
      (should= 1 (cyclomatic-complexity
        "(defn foo [x]\n  \"(if true 1 0)\"\n  x)")))

    (it "handles escaped quotes in strings"
      (should= 1 (cyclomatic-complexity
        (str "(defn foo [x]\n"
             "  \"a \\\"(if true)\\\" b\"\n"
             "  x)")))))

  (context "extract-functions"
    (it "extracts function name and line range from source text"
      (let [source "(ns foo)\n\n(defn bar [x]\n  (if x 1 0))\n\n(defn baz [y]\n  y)"
            fns (extract-functions source)]
        (should= 2 (count fns))
        (should= "bar" (:name (first fns)))
        (should= 3 (:start-line (first fns)))
        (should= 4 (:end-line (first fns)))
        (should= "baz" (:name (second fns)))
        (should= 6 (:start-line (second fns)))
        (should= 7 (:end-line (second fns)))))

    (it "handles defn- (private functions)"
      (let [source "(defn- helper [x]\n  x)"
            fns (extract-functions source)]
        (should= 1 (count fns))
        (should= "helper" (:name (first fns)))))

    (it "computes CC for each extracted function"
      (let [source "(defn simple [x] x)\n\n(defn branchy [x]\n  (if x\n    (when x 1)\n    0))"
            fns (extract-functions source)]
        (should= 1 (:complexity (first fns)))
        (should= 3 (:complexity (second fns)))))))
