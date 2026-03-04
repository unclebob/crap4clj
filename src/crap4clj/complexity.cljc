;; mutation-tested: 2026-03-04
(ns crap4clj.complexity
  (:require [clojure.string :as str]))

(def ^:private decision-point-pattern
  #"\((if-not|if-let|if-some|when-not|when-let|when-some|when-first|if|when|and|or|loop|catch)[\s\)]")

(def ^:private cond-form-pattern
  #"\((some->>|some->|cond->>|cond->|cond|condp|case)[\s\)]")

(defn- strip-strings [text]
  (str/replace text #"\"(?:[^\"\\]|\\.)*\"" "\"\""))

(defn- strip-comments [text]
  (->> (str/split-lines text)
       (map #(str/replace % #";.*" ""))
       (str/join "\n")))

(defn- open-bracket? [ch]
  (or (= ch \() (= ch \{) (= ch \[)))

(defn- close-bracket? [ch]
  (or (= ch \)) (= ch \}) (= ch \])))

(defn- top-level-form-start? [depth in-form]
  (and (= depth 1) (not in-form)))

(defn- maybe-inc-forms [forms depth in-form]
  (if (top-level-form-start? depth in-form)
    (inc forms)
    forms))

(defn- next-state-open [depth forms in-form]
  [(inc depth) (maybe-inc-forms forms depth in-form) true])

(defn- next-state-close [depth forms in-form]
  [(dec depth) forms (if (= depth 1) false in-form)])

(defn- next-state-whitespace [depth forms]
  [depth forms (not (= depth 1))])

(defn- next-state-token [depth forms in-form]
  [depth (maybe-inc-forms forms depth in-form) true])

(defn- line-after-newline [line newline?]
  (if newline?
    (inc line)
    line))

(defn- mode-after-comment [newline?]
  (if newline?
    :normal
    :comment))

(defn- maybe-top-level-start [at-top-level? candidate current]
  (if at-top-level?
    candidate
    current))

(defn- clear-when [pred value]
  (if pred nil value))

(defn- done-counting-top-level-forms? [text i depth]
  (or (>= i (count text)) (zero? depth)))

(defn- top-level-next-state [ch depth forms in-form]
  (cond
    (open-bracket? ch) (next-state-open depth forms in-form)
    (close-bracket? ch) (next-state-close depth forms in-form)
    (Character/isWhitespace ch) (next-state-whitespace depth forms)
    :else (next-state-token depth forms in-form)))

(defn- count-top-level-forms
  "Counts top-level forms in text starting at idx (inside an open paren).
   Returns the count of forms at depth 1 before the matching close paren."
  [text start-idx]
  (loop [i start-idx, depth 1, forms 0, in-form false]
    (if (done-counting-top-level-forms? text i depth)
      forms
      (let [ch (nth text i)]
        (let [[next-depth next-forms next-in-form]
              (top-level-next-state ch depth forms in-form)]
          (recur (inc i) next-depth next-forms next-in-form))))))

(defn- skip-to-body
  "Returns index just past the form keyword (e.g., past 'cond' in '(cond ...')."
  [text match-start]
  (let [after-paren (inc match-start)]
    (loop [i after-paren]
      (if (or (>= i (count text))
              (Character/isWhitespace (nth text i))
              (= (nth text i) \)))
        i
        (recur (inc i))))))

(def ^:private form-skip-count
  {"condp" 2
   "case" 1
   "cond->" 1
   "cond->>" 1
   "some->" 1
   "some->>" 1})

(def ^:private thread-form-types #{"some->" "some->>"})

(defn- pairwise-clause-count [form-type remaining]
  (let [case-default? (and (= form-type "case") (odd? remaining))
        base (quot remaining 2)]
    (if case-default?
      (inc base)
      base)))

(defn- count-clauses [text form-type match-start]
  (let [body-start (skip-to-body text match-start)
        total-forms (count-top-level-forms text body-start)
        skip (get form-skip-count form-type 0)
        remaining (- total-forms skip)]
    (if (thread-form-types form-type)
      remaining
      (pairwise-clause-count form-type remaining))))

(defn- count-cond-decisions [clean]
  (let [matcher (re-matcher cond-form-pattern clean)]
    (loop [total 0]
      (if-not (.find matcher)
        total
        (let [form-type (second (re-find cond-form-pattern (.group matcher)))
              match-start (.start matcher)]
          (recur (+ total (count-clauses clean form-type match-start))))))))

(defn cyclomatic-complexity [fn-text]
  (let [clean (-> fn-text strip-strings strip-comments)
        simple (count (re-seq decision-point-pattern clean))
        cond-decisions (count-cond-decisions clean)]
    (+ 1 simple cond-decisions)))

(def ^:private defn-pattern #"(?s)^\(\s*defn-?\s+([^\s\(\)\[\]\{\}\"]+)")

(defn- extract-top-level-defn-forms [source]
  (let [n (count source)]
    (loop [i 0
           line 1
           depth 0
           mode :normal
           escaped false
           form-start-idx nil
           form-start-line nil
           forms []]
      (if (>= i n)
        forms
        (let [ch (nth source i)
              newline? (= ch \newline)]
          (cond
            (= mode :comment)
            (recur (inc i)
                   (line-after-newline line newline?)
                   depth
                   (mode-after-comment newline?)
                   false
                   form-start-idx
                   form-start-line
                   forms)

            (= mode :string)
            (cond
              escaped
              (recur (inc i) (line-after-newline line newline?) depth :string false
                     form-start-idx form-start-line forms)

              (= ch \\)
              (recur (inc i) line depth :string true
                     form-start-idx form-start-line forms)

              (= ch \")
              (recur (inc i) line depth :normal false
                     form-start-idx form-start-line forms)

              :else
              (recur (inc i) (line-after-newline line newline?) depth :string false
                     form-start-idx form-start-line forms))

            :else
            (cond
              (= ch \;)
              (recur (inc i) line depth :comment false
                     form-start-idx form-start-line forms)

              (= ch \")
              (recur (inc i) line depth :string false
                     form-start-idx form-start-line forms)

              (= ch \()
              (let [at-top-level? (zero? depth)
                    next-depth (inc depth)]
                (recur (inc i)
                       line
                       next-depth
                       :normal
                       false
                       (maybe-top-level-start at-top-level? i form-start-idx)
                       (maybe-top-level-start at-top-level? line form-start-line)
                       forms))

              (= ch \))
              (let [next-depth (max 0 (dec depth))
                    closes-top-level-form? (and (= depth 1) (some? form-start-idx))
                    forms' (if-not closes-top-level-form?
                             forms
                             (let [form-text (subs source form-start-idx (inc i))]
                               (if-let [m (re-find defn-pattern form-text)]
                                 (conj forms {:name (second m)
                                              :start-line form-start-line
                                              :end-line line
                                              :text form-text})
                                 forms)))]
                (recur (inc i)
                       line
                       next-depth
                       :normal
                       false
                       (clear-when closes-top-level-form? form-start-idx)
                       (clear-when closes-top-level-form? form-start-line)
                       forms'))

              :else
              (recur (inc i)
                     (line-after-newline line newline?)
                     depth
                     :normal
                     false
                     form-start-idx
                     form-start-line
                     forms))))))))

(defn extract-functions [source]
  (->> (extract-top-level-defn-forms source)
       (mapv (fn [{:keys [name start-line end-line text]}]
               {:name name
                :start-line start-line
                :end-line end-line
                :complexity (cyclomatic-complexity text)}))))
