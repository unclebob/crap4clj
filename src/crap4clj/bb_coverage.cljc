(ns crap4clj.bb-coverage)

(def ^:private bb-extension ".bb")
(def ^:private eof-marker (Object.))

(defn- add-bb-extension [extensions]
  (if (some #{bb-extension} extensions)
    extensions
    (conj extensions bb-extension)))

(defn- resolve-required-var [sym]
  (or (requiring-resolve sym)
      (throw (ex-info (str "Cannot resolve " sym) {:symbol sym}))))

(defn- add-bb-reader-feature [read-opts]
  (update read-opts :features (fnil conj #{}) :bb))

(defn- read-bb-forms [source-reader]
  (let [read-form (resolve-required-var 'clojure.tools.reader/read)
        data-readers-var
        (resolve-required-var 'clojure.tools.reader/*data-readers*)
        read-next (fn []
                    (binding [*read-eval* false]
                      (with-bindings {data-readers-var *data-readers*}
                        (read-form {:eof eof-marker
                                    :features #{:bb :clj}
                                    :read-cond :allow}
                                   source-reader))))]
    (take-while #(not= eof-marker %) (repeatedly read-next))))

(defn enable-bb-sources! []
  (let [file-extensions-var
        (resolve-required-var 'clojure.tools.namespace.file/clojure-extensions)
        clj-platform-var
        (resolve-required-var 'clojure.tools.namespace.find/clj)
        clj-read-opts-var
        (resolve-required-var 'clojure.tools.namespace.parse/clj-read-opts)
        source-forms-var
        (resolve-required-var 'cloverage.source/forms)]
    (alter-var-root file-extensions-var add-bb-extension)
    (alter-var-root clj-platform-var
                    #(-> %
                         (update :extensions add-bb-extension)
                         (update :read-opts add-bb-reader-feature)))
    (alter-var-root clj-read-opts-var add-bb-reader-feature)
    (alter-var-root source-forms-var (constantly read-bb-forms))))

(defn -main [& args]
  (enable-bb-sources!)
  (apply (resolve-required-var 'cloverage.coverage/-main) args))
