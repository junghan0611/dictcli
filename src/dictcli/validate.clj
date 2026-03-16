(ns dictcli.validate
  "graph.edn 트리플 인바리언트 검증

   모든 [entity relation value]는:
   1. entity: 문자열, 비어있지 않음
   2. relation: 허용된 키워드
   3. :trans value → [a-z0-9]만, 50자 이하
   4. :source value → YYYYMMDDTHHMMSS 형식
   5. 중복 금지"
  (:require [clojure.string :as str]
            [dictcli.graph :as g]))

;; ── 인바리언트 검사 함수들 ────────────────────────

(defn check-structure
  "기본 구조: 3원소 벡터, 문자열+키워드+문자열"
  [[e r v :as triple]]
  (cond
    (not (vector? triple))
    {:error :not-vector :triple triple}

    (not= 3 (count triple))
    {:error :wrong-arity :triple triple :count (count triple)}

    (not (string? e))
    {:error :entity-not-string :triple triple :type (type e)}

    (not (keyword? r))
    {:error :relation-not-keyword :triple triple :type (type r)}

    (not (string? v))
    {:error :value-not-string :triple triple :type (type v)}

    (str/blank? e)
    {:error :entity-blank :triple triple}

    (str/blank? v)
    {:error :value-blank :triple triple}))

(defn check-relation
  "허용된 관계 타입인지"
  [[_ r _ :as triple]]
  (when-not (contains? g/relation-types r)
    {:error :invalid-relation :triple triple :relation r
     :allowed (keys g/relation-types)}))

(defn check-trans-value
  ":trans 값이 [a-z0-9]만인지 — Denote 태그 호환"
  [[e r v :as triple]]
  (when (= r :trans)
    (cond
      (re-find #"[A-Z]" v)
      {:error :trans-uppercase :triple triple :value v}

      (re-find #"\s" v)
      {:error :trans-whitespace :triple triple :value v}

      (re-find #"[가-힣]" v)
      {:error :trans-korean :triple triple :value v}

      (re-find #"[-_]" v)
      {:error :trans-hyphen :triple triple :value v}

      (> (count v) 50)
      {:error :trans-too-long :triple triple :length (count v)}

      (not (re-matches #"[a-z0-9]+" v))
      {:error :trans-invalid-chars :triple triple :value v})))

(defn check-source-value
  ":source 값이 YYYYMMDDTHHMMSS 형식인지"
  [[_ r v :as triple]]
  (when (= r :source)
    (when-not (re-matches #"\d{8}T\d{6}" v)
      {:error :source-invalid-format :triple triple :value v})))

(defn check-value-length
  "value 50자 이하"
  [[_ _ v :as triple]]
  (when (> (count v) 50)
    {:error :value-too-long :triple triple :length (count v)}))

;; ── 전체 검증 ─────────────────────────────────────

(defn validate-triple
  "단일 트리플 검증. 에러 목록 반환 (빈 목록이면 OK)"
  [triple]
  (filterv some?
           [(check-structure triple)
            (check-relation triple)
            (check-trans-value triple)
            (check-source-value triple)
            (check-value-length triple)]))

(defn validate-graph
  "전체 그래프 검증. {:ok? bool :errors [...] :warnings [...] :stats {...}}"
  [triples]
  (let [;; 각 트리플 검증
        all-errors (mapcat validate-triple triples)

        ;; 중복 검사
        dupes (->> (frequencies triples)
                   (filter #(> (val %) 1))
                   (map (fn [[t cnt]]
                          {:error :duplicate :triple t :count cnt})))

        errors (into (vec all-errors) dupes)

        ;; 통계
        by-rel (frequencies (map second triples))
        trans-count (count (filter #(= (second %) :trans) triples))]

    {:ok?      (empty? errors)
     :errors   errors
     :stats    {:triples (count triples)
                :relations by-rel
                :trans trans-count
                :duplicates (count dupes)}
     :summary  (str (count triples) " triples, "
                    (count errors) " errors"
                    (when (seq dupes) (str ", " (count dupes) " duplicates")))}))

;; ── 출력 ──────────────────────────────────────────

(defn format-error [{:keys [error triple value length]}]
  (case error
    :trans-uppercase   (str "  ❌ 대문자: " (pr-str triple))
    :trans-whitespace  (str "  ❌ 공백: " (pr-str triple))
    :trans-korean      (str "  ❌ 한글: " (pr-str triple))
    :trans-hyphen      (str "  ❌ 하이픈: " (pr-str triple))
    :trans-too-long    (str "  ❌ 길이(" length "): " (pr-str triple))
    :trans-invalid-chars (str "  ❌ 잘못된 문자: " (pr-str triple))
    :source-invalid-format (str "  ❌ source 형식: " (pr-str triple))
    :value-too-long    (str "  ❌ value 길이(" length "): " (pr-str triple))
    :duplicate         (str "  ❌ 중복: " (pr-str triple))
    :invalid-relation  (str "  ❌ 잘못된 관계: " (pr-str triple))
    (str "  ❌ " error ": " (pr-str triple))))
