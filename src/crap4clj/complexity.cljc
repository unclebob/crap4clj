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

(defn- count-top-level-forms
  "Counts top-level forms in text starting at idx (inside an open paren).
   Returns the count of forms at depth 1 before the matching close paren."
  [text start-idx]
  (loop [i start-idx, depth 1, forms 0, in-form false]
    (if (or (>= i (count text)) (zero? depth))
      forms
      (let [ch (nth text i)]
        (cond
          (open-bracket? ch)
          (recur (inc i) (inc depth)
                 (if (and (= depth 1) (not in-form)) (inc forms) forms)
                 true)

          (close-bracket? ch)
          (recur (inc i) (dec depth) forms
                 (if (= depth 1) false in-form))

          (Character/isWhitespace ch)
          (recur (inc i) depth forms
                 (if (= depth 1) false in-form))

          :else
          (recur (inc i) depth
                 (if (and (= depth 1) (not in-form)) (inc forms) forms)
                 true))))))

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

(defn- count-clauses [text form-type match-start]
  (let [body-start (skip-to-body text match-start)
        total-forms (count-top-level-forms text body-start)
        skip (case form-type "condp" 2 "case" 1 "cond->" 1 "cond->>" 1
                             "some->" 1 "some->>" 1 0)
        remaining (- total-forms skip)
        has-default? (and (= form-type "case") (odd? remaining))]
    (case form-type
      ("some->" "some->>") remaining
      (if has-default?
        (inc (quot remaining 2))
        (quot remaining 2)))))

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
                   (if newline? (inc line) line)
                   depth
                   (if newline? :normal :comment)
                   false
                   form-start-idx
                   form-start-line
                   forms)

            (= mode :string)
            (cond
              escaped
              (recur (inc i) (if newline? (inc line) line) depth :string false
                     form-start-idx form-start-line forms)

              (= ch \\)
              (recur (inc i) line depth :string true
                     form-start-idx form-start-line forms)

              (= ch \")
              (recur (inc i) line depth :normal false
                     form-start-idx form-start-line forms)

              :else
              (recur (inc i) (if newline? (inc line) line) depth :string false
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
                       (if at-top-level? i form-start-idx)
                       (if at-top-level? line form-start-line)
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
                       (if closes-top-level-form? nil form-start-idx)
                       (if closes-top-level-form? nil form-start-line)
                       forms'))

              :else
              (recur (inc i)
                     (if newline? (inc line) line)
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
