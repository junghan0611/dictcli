(ns dictcli.parser-test
  (:require [clojure.test :refer [deftest testing is]]
            [dictcli.parser :as parser]))

(deftest parse-term-line-test
  (testing "기본 용어 파싱"
    (is (= {:word "존재" :definition "being, Sein."}
           (parser/parse-term-line "<<존재>> :: being, Sein."))))

  (testing "빈 정의"
    (is (= {:word "테스트" :definition ""}
           (parser/parse-term-line "<<테스트>> :: "))))

  (testing "용어 아닌 줄"
    (is (nil? (parser/parse-term-line "# 섹션 헤딩")))
    (is (nil? (parser/parse-term-line "일반 텍스트")))
    (is (nil? (parser/parse-term-line "")))))

(deftest detect-lang-test
  (testing "한글"
    (is (= :ko (parser/detect-lang "존재"))))
  (testing "영어"
    (is (= :en (parser/detect-lang "being"))))
  (testing "독일어"
    (is (= :de (parser/detect-lang "Öffentlichkeit")))))

(deftest parse-glossary-file-test
  (testing "샘플 파일 파싱"
    (let [result (parser/parse-glossary-file "data/sample.txt")]
      (is (seq (:terms result)))
      (is (= "sample" (get-in result [:meta :title]))))))
