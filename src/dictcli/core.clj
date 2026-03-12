(ns dictcli.core
  "dictcli — 개인 어휘 사전 CLI"
  (:require [dictcli.parser :as parser]
            [dictcli.db :as db]
            [dictcli.wordmap :as wordmap]
            [clojure.string :as str])
  (:gen-class))

(def default-db "dictcli.db")

(defn build-from-glossary
  "glossary 디렉토리 → SQLite"
  [dirs db-path]
  (let [ds (db/datasource db-path)]
    (db/init-db! ds)
    (db/with-transaction ds
      (fn [tx]
        (doseq [dir dirs]
          (println (str "📖 파싱: " dir))
          (let [results (parser/parse-directory dir)]
            (doseq [{:keys [terms filepath]} results]
              (when (seq terms)
                (println (str "  " (count terms) " 용어 ← " filepath))
                (doseq [term terms]
                  (db/insert-term! tx term))))))))
    (let [stats (db/stats ds)]
      (println (str "\n✅ 빌드 완료: " (:terms stats) " 용어 → " db-path)))))

(defn build-from-wordmap
  "saiculture wordmap.json → SQLite (frequency + cooccurrence)"
  [wordmap-path db-path]
  (let [ds (db/datasource db-path)]
    (db/init-db! ds)
    (println (str "📊 워드맵 로딩: " wordmap-path))
    (let [{:keys [frequency cooccurrence]} (wordmap/parse-wordmap wordmap-path)
          freq-count (count frequency)
          co-count (count cooccurrence)]
      (db/with-transaction ds
        (fn [tx]
          (doseq [[word cnt] frequency]
            (db/insert-freq! tx (name word) cnt "saiculture"))
          (doseq [[pair cnt] cooccurrence]
            (let [[w1 w2] (str/split (name pair) #" \+ ")]
              (db/insert-cooccur! tx w1 w2 cnt "saiculture")))))
      (println (str "✅ 워드맵: " freq-count " 빈도 + " co-count " 동시출현 → " db-path)))))

(defn cmd-build
  "build 커맨드"
  [args]
  (let [db-path (or (first (filter #(str/ends-with? % ".db") args)) default-db)
        dirs (or (seq (remove #(str/ends-with? % ".db") args))
                 [(str (System/getProperty "user.home") "/sync/org/dict")])]
    (build-from-glossary dirs db-path)
    ;; saiculture wordmap도 있으면 추가
    (let [wordmap-path (str (System/getProperty "user.home")
                            "/repos/gh/naver-saiculture/wordmap.json")]
      (when (.exists (java.io.File. wordmap-path))
        (build-from-wordmap wordmap-path db-path)))))

(defn cmd-lookup
  "lookup 커맨드"
  [word]
  (let [ds (db/datasource default-db)
        terms (db/lookup ds word)
        freqs (db/get-freq ds word)
        related (db/get-related ds word)]
    (if (empty? terms)
      (println (str "❌ \"" word "\" 없음"))
      (do
        (println (str "🔍 \"" word "\" — " (count terms) "건"))
        (println)
        (doseq [{:keys [word lang definition source section]} terms]
          (println (str "  " word " [" lang "] " definition))
          (when section (println (str "    섹션: " section)))
          (when source (println (str "    출처: " source))))
        (when (seq freqs)
          (println)
          (println "📊 빈도:")
          (doseq [{:keys [count source]} freqs]
            (println (str "  " count " (" source ")"))))
        (when (seq related)
          (println)
          (println "🔗 연관:")
          (doseq [{:keys [related count]} related]
            (println (str "  " related " (" count ")"))))))))

(defn cmd-related
  "related 커맨드"
  [word]
  (let [ds (db/datasource default-db)
        related (db/get-related ds word)]
    (if (empty? related)
      (println (str "❌ \"" word "\" 연관 단어 없음"))
      (do
        (println (str "🔗 \"" word "\" 연관 단어:"))
        (doseq [{:keys [related count]} related]
          (println (str "  " related " (" count ")")))))))

(defn cmd-stats
  "stats 커맨드"
  []
  (let [ds (db/datasource default-db)
        s (db/stats ds)]
    (println "📊 dictcli 통계:")
    (println (str "  용어:     " (:terms s)))
    (println (str "  빈도:     " (:freq s)))
    (println (str "  동시출현: " (:cooccur s)))
    (println (str "  매핑:     " (:mappings s)))))

(defn -main [& args]
  (let [cmd (first args)
        rest-args (rest args)]
    (case cmd
      "build"   (cmd-build rest-args)
      "lookup"  (if (first rest-args)
                  (cmd-lookup (first rest-args))
                  (println "Usage: dictcli lookup <word>"))
      "related" (if (first rest-args)
                  (cmd-related (first rest-args))
                  (println "Usage: dictcli related <word>"))
      "stats"   (cmd-stats)
      (do
        (println "dictcli — 개인 어휘 사전 CLI")
        (println)
        (println "Usage:")
        (println "  dictcli build   [DIR...]     glossary + wordmap → SQLite")
        (println "  dictcli lookup  <word>       용어 검색")
        (println "  dictcli related <word>       연관 단어")
        (println "  dictcli stats                DB 통계")))))
