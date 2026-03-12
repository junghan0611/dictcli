(ns dictcli.db
  "SQLite 인덱스 빌드/쿼리"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def default-db-path "dictcli.db")

(defn datasource [db-path]
  (jdbc/get-datasource {:dbtype "sqlite" :dbname db-path}))

(def schema-ddl
  ["CREATE TABLE IF NOT EXISTS terms (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      word TEXT NOT NULL,
      lang TEXT NOT NULL,
      definition TEXT,
      source TEXT,
      domain TEXT,
      section TEXT,
      line_no INTEGER
    )"
   "CREATE TABLE IF NOT EXISTS mappings (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      word_id INTEGER REFERENCES terms(id),
      target_word TEXT NOT NULL,
      target_lang TEXT NOT NULL,
      my_choice BOOLEAN DEFAULT FALSE
    )"
   "CREATE TABLE IF NOT EXISTS cooccur (
      word1 TEXT NOT NULL,
      word2 TEXT NOT NULL,
      count INTEGER NOT NULL,
      source TEXT
    )"
   "CREATE TABLE IF NOT EXISTS freq (
      word TEXT NOT NULL,
      count INTEGER NOT NULL,
      source TEXT NOT NULL
    )"
   ;; 인덱스
   "CREATE INDEX IF NOT EXISTS idx_terms_word ON terms(word)"
   "CREATE INDEX IF NOT EXISTS idx_terms_lang ON terms(lang)"
   "CREATE INDEX IF NOT EXISTS idx_freq_word ON freq(word)"
   "CREATE INDEX IF NOT EXISTS idx_cooccur_word1 ON cooccur(word1)"
   "CREATE INDEX IF NOT EXISTS idx_cooccur_word2 ON cooccur(word2)"])

(defn init-db!
  "스키마 초기화"
  [ds]
  (doseq [ddl schema-ddl]
    (jdbc/execute! ds [ddl])))

(defn with-transaction
  "트랜잭션으로 감싸서 bulk insert 성능 향상"
  [ds f]
  (jdbc/with-transaction [tx ds]
    (f tx)))

(defn insert-term!
  "용어 삽입"
  [ds {:keys [word lang definition source domain section line-no]}]
  (jdbc/execute! ds
    ["INSERT INTO terms (word, lang, definition, source, domain, section, line_no)
      VALUES (?, ?, ?, ?, ?, ?, ?)"
     word (name lang) definition source domain section line-no]))

(defn insert-freq!
  "빈도 삽입"
  [ds word count source]
  (jdbc/execute! ds
    ["INSERT INTO freq (word, count, source) VALUES (?, ?, ?)"
     word count source]))

(defn insert-cooccur!
  "동시출현 삽입"
  [ds word1 word2 count source]
  (jdbc/execute! ds
    ["INSERT INTO cooccur (word1, word2, count, source) VALUES (?, ?, ?, ?)"
     word1 word2 count source]))

(defn lookup
  "용어 검색 — 부분 매칭"
  [ds word]
  (jdbc/execute! ds
    ["SELECT * FROM terms WHERE word LIKE ? ORDER BY word"
     (str "%" word "%")]
    {:builder-fn rs/as-unqualified-maps}))

(defn get-freq
  "빈도 조회"
  [ds word]
  (jdbc/execute! ds
    ["SELECT * FROM freq WHERE word = ? ORDER BY count DESC"
     word]
    {:builder-fn rs/as-unqualified-maps}))

(defn get-related
  "연관 단어 (cooccurrence)"
  [ds word]
  (jdbc/execute! ds
    ["SELECT word2 as related, count FROM cooccur WHERE word1 = ?
      UNION
      SELECT word1 as related, count FROM cooccur WHERE word2 = ?
      ORDER BY count DESC"
     word word]
    {:builder-fn rs/as-unqualified-maps}))

(defn stats
  "DB 통계"
  [ds]
  {:terms    (-> (jdbc/execute-one! ds ["SELECT count(*) as c FROM terms"]) :c)
   :freq     (-> (jdbc/execute-one! ds ["SELECT count(*) as c FROM freq"]) :c)
   :cooccur  (-> (jdbc/execute-one! ds ["SELECT count(*) as c FROM cooccur"]) :c)
   :mappings (-> (jdbc/execute-one! ds ["SELECT count(*) as c FROM mappings"]) :c)})
