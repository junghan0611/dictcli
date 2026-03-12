(ns dictcli.wordmap
  "saiculture wordmap.json 파서"
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

;; clojure.data.json이 없으면 직접 파싱 — deps.edn에 추가 필요시
;; 일단 slurp + read-string으로 대체 가능

(defn parse-wordmap
  "wordmap.json 파싱. {:frequency {word count} :cooccurrence_top100 {pair count}}"
  [filepath]
  ;; TODO: clojure.data.json 또는 cheshire 추가 후 구현
  (println "TODO: parse" filepath))
