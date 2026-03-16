(ns dictcli.graph-test
  (:require [clojure.test :refer [deftest is testing]]
            [dictcli.graph :as g]))

(def sample-triples
  [["보편"   :trans    "universal"]
   ["보편"   :trans    "universalism"]
   ["보편"   :opposite "특수"]
   ["특수"   :trans    "particular"]
   ["보편"   :related  "파이데이아"]
   ["파이데이아" :trans "paideia"]
   ["보편"   :source   "20250424T233558"]])

(def sample-index (g/build-index sample-triples))

(deftest test-valid-triple
  (testing "유효한 트리플"
    (is (g/valid-triple? ["보편" :trans "universal"]))
    (is (g/valid-triple? ["도피" :opposite "대면"])))
  (testing "잘못된 트리플"
    (is (not (g/valid-triple? ["보편" :unknown "x"])))
    (is (not (g/valid-triple? [123 :trans "x"])))
    (is (not (g/valid-triple? ["a" "trans" "b"])))))

(deftest test-translations
  (testing "보편의 번역"
    (let [trans (g/translations sample-index "보편")]
      (is (some #{"universal"} trans))
      (is (some #{"universalism"} trans))))
  (testing "역방향 — universal의 번역"
    (let [trans (g/translations sample-index "universal")]
      (is (some #{"보편"} trans)))))

(deftest test-opposites
  (testing "보편의 대극"
    (is (some #{"특수"} (g/opposites sample-index "보편"))))
  (testing "역방향 — 특수의 대극"
    (is (some #{"보편"} (g/opposites sample-index "특수")))))

(deftest test-expand
  (testing "보편 확장 — 번역 + 대극번역 + 관련어번역"
    (let [expanded (set (g/expand sample-index "보편"))]
      ;; 직접 번역
      (is (contains? expanded "universal"))
      (is (contains? expanded "universalism"))
      ;; 대극(특수)의 번역
      (is (contains? expanded "particular"))
      ;; 관련어(파이데이아)의 번역
      (is (contains? expanded "paideia")))))

(deftest test-add-triple
  (testing "트리플 추가"
    (let [result (g/add-triple [] ["존재" :trans "being"])]
      (is (= 1 (count result)))))
  (testing "중복 방지"
    (let [result (-> []
                     (g/add-triple ["존재" :trans "being"])
                     (g/add-triple ["존재" :trans "being"]))]
      (is (= 1 (count result)))))
  (testing "대칭 관계 자동 추가"
    (let [result (g/add-triple [] ["보편" :opposite "특수"])]
      (is (= 2 (count result)))
      (is (some #(= % ["특수" :opposite "보편"]) result)))))

(deftest test-cluster
  (testing "클러스터 멤버"
    (let [members (g/cluster-members sample-index "20250424T233558")]
      (is (some #{"보편"} members))
      ;; related로 연결된 파이데이아도 포함
      (is (some #{"파이데이아"} members)))))

(deftest test-stats
  (testing "통계"
    (let [s (g/stats sample-triples)]
      (is (= 7 (:triples s)))
      (is (pos? (:words s)))
      (is (= 1 (:clusters s))))))
