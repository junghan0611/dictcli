(ns dictcli.core
  "dictcli — 힣의 어휘 연결체 CLI
   
   코드가 곧 데이터. EDN 트리플 그래프."
  (:require [dictcli.graph :as g]
            [dictcli.normalize :as norm]
            [dictcli.validate :as v]
            [clojure.string :as str]
            [clojure.edn :as edn])
  (:gen-class))

;; stem 네임스는 :kiwi alias로 실행시만 로드 (JNI 의존)
(def ^:private stem-ns (delay (require 'dictcli.stem) (find-ns 'dictcli.stem)))

(defn graph-path
  "graph.edn 경로. 런타임에 DICTCLI_GRAPH 환경변수 확인."
  []
  (or (System/getenv "DICTCLI_GRAPH")
      "graph.edn"))

;; ── 그래프 로드 헬퍼 ──────────────────────────────

(defn load-graph
  "graph.edn 로드 + 인덱스 빌드"
  ([] (load-graph (graph-path)))
  ([path]
   (let [triples (g/load-graph path)]
     {:triples triples
      :index   (g/build-index triples)
      :path    path})))

;; ── 커맨드: add ───────────────────────────────────

(defn cmd-add
  "트리플 추가. 
   사용법: dictcli add <entity> <relation> <value>
   예: dictcli add 보편 :trans universal"
  [args]
  (let [[entity rel-str value] args]
    (if (or (nil? entity) (nil? rel-str) (nil? value))
      (do (println "Usage: dictcli add <entity> <relation> <value>")
          (println "  예: dictcli add 보편 :trans universal")
          (println "  관계: " (str/join " " (map #(str ":" (name %)) (keys g/relation-types)))))
      (let [rel (keyword (str/replace rel-str #"^:" ""))
            triple [entity rel value]
            triples (g/load-graph (graph-path))
            new-triples (g/add-triple triples triple)
            added (- (count new-triples) (count triples))]
        (g/save-graph (graph-path) new-triples)
        (println (str "✅ " (pr-str triple)))
        (when (> added 1)
          (println (str "   + 역방향 자동 추가 (" added "개)")))
        (println (str "📊 총 " (count new-triples) "개 트리플"))))))

;; ── 커맨드: graph ─────────────────────────────────

(defn cmd-graph
  "단어의 모든 연결 표시.
   사용법: dictcli graph <word>"
  [word]
  (let [{:keys [index]} (load-graph)
        all (g/query-word index word)]
    (if (empty? all)
      (println (str "❌ \"" word "\" — 연결 없음"))
      (let [grouped (group-by second all)]
        (println (str "🔗 \"" word "\" — " (count all) "개 연결\n"))
        (doseq [[rel triples] (sort-by #(name (key %)) grouped)]
          (println (str "  " (name rel) ":"))
          (doseq [[e _ v] triples]
            (if (= e word)
              (println (str "    → " v))
              (println (str "    ← " e)))))))))

;; ── 커맨드: expand ────────────────────────────────

(defn cmd-expand
  "쿼리 확장 — knowledge_search 연동용.
   사용법: dictcli expand <word> [--json]"
  [args]
  (let [word (first args)
        json? (some #{"--json"} args)
        {:keys [index]} (load-graph)
        expanded (g/expand index word)]
    (if (empty? expanded)
      (when-not json?
        (println (str "❌ \"" word "\" — 확장 불가")))
      (if json?
        ;; JSON 출력 (에이전트 연동)
        (println (str "[" (str/join "," (map #(str "\"" % "\"") expanded)) "]"))
        ;; 사람 읽기용
        (do
          (println (str "🔍 \"" word "\" 확장:"))
          (doseq [w expanded]
            (println (str "  " w))))))))

;; ── 커맨드: cluster ───────────────────────────────

(defn cmd-cluster
  "메타노트 클러스터 전체 표시.
   사용법: dictcli cluster <meta-id>"
  [meta-id]
  (let [{:keys [index]} (load-graph)
        members (g/cluster-members index meta-id)]
    (if (empty? members)
      (println (str "❌ 클러스터 \"" meta-id "\" 없음"))
      (do
        (println (str "📦 클러스터 " meta-id " — " (count members) "개 단어\n"))
        (doseq [word (sort members)]
          (let [trans (g/translations index word)]
            (println (str "  " word
                          (when (seq trans)
                            (str " → " (str/join ", " trans)))))))))))

;; ── 커맨드: stats ─────────────────────────────────

(defn cmd-stats
  "그래프 통계"
  []
  (let [triples (g/load-graph (graph-path))
        s (g/stats triples)]
    (println "📊 그래프 통계:")
    (println (str "  트리플:   " (:triples s)))
    (println (str "  단어:     " (:words s)))
    (println (str "  클러스터: " (:clusters s)))
    (println)
    (println "  관계별:")
    (doseq [[rel cnt] (sort-by val > (:relations s))]
      (println (str "    :" (name rel) "  " cnt)))
    (when (seq (:sources s))
      (println)
      (println "  메타노트:")
      (doseq [src (:sources s)]
        (println (str "    " src))))))

;; ── 커맨드: import ────────────────────────────────

(defn cmd-import
  "시드 EDN 파일을 graph.edn에 병합.
   사용법: dictcli import <file.edn>"
  [path]
  (if-not (.exists (java.io.File. (str path)))
    (println (str "❌ 파일 없음: " path))
    (let [existing (g/load-graph (graph-path))
          new-triples (g/load-graph path)
          valid (filter g/valid-triple? new-triples)
          invalid (remove g/valid-triple? new-triples)
          merged (g/add-triples existing valid)]
      (when (seq invalid)
        (println (str "⚠️  잘못된 트리플 " (count invalid) "개 건너뜀")))
      (g/save-graph (graph-path) merged)
      (println (str "✅ " (count valid) "개 임포트 → 총 " (count merged) "개 트리플")))))

;; ── 커맨드: normalize ──────────────────────────────

(defn cmd-normalize
  "graph.edn 정규화 — Denote 태그 호환"
  []
  (let [triples (g/load-graph (graph-path))
        _ (println (str "📋 정규화 전: " (count triples) "개 트리플"))
        normalized (norm/normalize-triples triples)]
    (g/save-graph (graph-path) normalized)
    (println)
    (println (str "✅ 정규화 완료: " (count normalized) "개 트리플"))
    ;; 검증
    (let [trans-vals (->> normalized
                         (filter #(= (second %) :trans))
                         (map #(nth % 2)))
          upper (count (filter #(re-find #"[A-Z]" %) trans-vals))
          spaced (count (filter #(re-find #" " %) trans-vals))]
      (println (str "  대문자: " upper (if (zero? upper) " ✅" " ⚠️")))
      (println (str "  공백:   " spaced (if (zero? spaced) " ✅" " ⚠️"))))))

;; ── 커맨드: validate ───────────────────────────────

(defn cmd-validate
  "graph.edn 인바리언트 검증"
  []
  (let [triples (g/load-graph (graph-path))
        result (v/validate-graph triples)]
    (println (str "🔍 검증: " (:summary result)))
    (println)
    (if (:ok? result)
      (do (println "✅ 모든 인바리언트 통과")
          (println (str "   :trans " (get-in result [:stats :trans]) "개")))
      (do (println (str "❌ " (count (:errors result)) "개 위반:"))
          (println)
          (doseq [err (:errors result)]
            (println (v/format-error err)))
          ;; exit code 1 for CI
          (System/exit 1)))))

;; ── 커맨드: stem ─────────────────────────────────

(defn cmd-stem
  "Kiwi 형태소 분석 → 명사 어간 추출 + expand 확장.
   사용법: dictcli stem <문장> [--tokens]"
  [args]
  (let [text (first args)
        tokens? (some #{"--tokens"} args)]
    (if (nil? text)
      (println "Usage: dictcli stem <\"문장\"> [--tokens]")
      (do
        @stem-ns  ;; lazy load Kiwi
        (let [stem-fn (ns-resolve @stem-ns 'stems)
              tok-fn  (ns-resolve @stem-ns 'tokenize)]
          (when tokens?
            (println "🔍 토큰:")
            (doseq [t (tok-fn text)]
              (println (str "  " (:form t) "\t" (name (:tag t))))))
          (let [noun-stems (stem-fn text)]
            (println (str "🌱 stem: " (str/join ", " noun-stems)))
            ;; expand 연결
            (let [{:keys [index]} (load-graph)]
              (doseq [s noun-stems]
                (let [expanded (g/expand index s)]
                  (when (seq expanded)
                    (println (str "  🔍 " s " → " (str/join ", " expanded))))))
            (println (str "📊 어간 " (count noun-stems) "개"))))))))

;; ── 메인 ──────────────────────────────────────────

(defn -main [& args]
  (let [cmd (first args)
        rest-args (rest args)]
    (case cmd
      "add"       (cmd-add rest-args)
      "graph"     (if (first rest-args)
                    (cmd-graph (first rest-args))
                    (println "Usage: dictcli graph <word>"))
      "expand"    (if (first rest-args)
                    (cmd-expand rest-args)
                    (println "Usage: dictcli expand <word> [--json]"))
      "cluster"   (if (first rest-args)
                    (cmd-cluster (first rest-args))
                    (println "Usage: dictcli cluster <meta-id>"))
      "stats"     (cmd-stats)
      "import"    (if (first rest-args)
                    (cmd-import (first rest-args))
                    (println "Usage: dictcli import <file.edn>"))
      "normalize" (cmd-normalize)
      "validate" (cmd-validate)
      "stem"     (cmd-stem rest-args)
      ;; 도움말
      (do
        (println "dictcli — 힣의 어휘 연결체")
        (println)
        (println "Usage:")
        (println "  dictcli add <entity> <rel> <value>   트리플 추가")
        (println "  dictcli graph <word>                 단어의 모든 연결")
        (println "  dictcli expand <word> [--json]       쿼리 확장 (한→영)")
        (println "  dictcli cluster <meta-id>            메타노트 클러스터")
        (println "  dictcli stats                        그래프 통계")
        (println "  dictcli import <file.edn>            시드 데이터 병합")
        (println "  dictcli stem <\"문장\"> [--tokens]   Kiwi 어간 추출 + expand")
        (println)
        (println "관계 타입:")
        (doseq [[k v] (sort-by key g/relation-types)]
          (println (str "  :" (name k) "  — " v))))))))
