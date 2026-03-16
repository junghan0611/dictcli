(ns dictcli.core
  "dictcli — 힣의 어휘 연결체 CLI
   
   코드가 곧 데이터. EDN 트리플 그래프."
  (:require [dictcli.graph :as g]
            [clojure.string :as str]
            [clojure.edn :as edn])
  (:gen-class))

(def default-graph "graph.edn")

;; ── 그래프 로드 헬퍼 ──────────────────────────────

(defn load-graph
  "graph.edn 로드 + 인덱스 빌드"
  ([] (load-graph default-graph))
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
            triples (g/load-graph default-graph)
            new-triples (g/add-triple triples triple)
            added (- (count new-triples) (count triples))]
        (g/save-graph default-graph new-triples)
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
  (let [triples (g/load-graph default-graph)
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
    (let [existing (g/load-graph default-graph)
          new-triples (g/load-graph path)
          valid (filter g/valid-triple? new-triples)
          invalid (remove g/valid-triple? new-triples)
          merged (g/add-triples existing valid)]
      (when (seq invalid)
        (println (str "⚠️  잘못된 트리플 " (count invalid) "개 건너뜀")))
      (g/save-graph default-graph merged)
      (println (str "✅ " (count valid) "개 임포트 → 총 " (count merged) "개 트리플")))))

;; ── 메인 ──────────────────────────────────────────

(defn -main [& args]
  (let [cmd (first args)
        rest-args (rest args)]
    (case cmd
      "add"     (cmd-add rest-args)
      "graph"   (if (first rest-args)
                  (cmd-graph (first rest-args))
                  (println "Usage: dictcli graph <word>"))
      "expand"  (if (first rest-args)
                  (cmd-expand rest-args)
                  (println "Usage: dictcli expand <word> [--json]"))
      "cluster" (if (first rest-args)
                  (cmd-cluster (first rest-args))
                  (println "Usage: dictcli cluster <meta-id>"))
      "stats"   (cmd-stats)
      "import"  (if (first rest-args)
                  (cmd-import (first rest-args))
                  (println "Usage: dictcli import <file.edn>"))
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
        (println)
        (println "관계 타입:")
        (doseq [[k v] (sort-by key g/relation-types)]
          (println (str "  :" (name k) "  — " v)))))))
