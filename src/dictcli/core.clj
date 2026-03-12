(ns dictcli.core
  "dictcli — 개인 어휘 사전 CLI
   
   Usage:
     clj -M:run build   [--sources DIR...]  소스 → SQLite 빌드
     clj -M:run lookup  WORD                용어 검색
     clj -M:run related WORD                연관 단어
     clj -M:run stats                       통계"
  (:require [dictcli.parser :as parser]
            [dictcli.db :as db]))

(defn -main [& args]
  (let [cmd (first args)
        rest-args (rest args)]
    (case cmd
      "build"   (println "TODO: build")
      "lookup"  (println "TODO: lookup" (first rest-args))
      "related" (println "TODO: related" (first rest-args))
      "stats"   (println "TODO: stats")
      (println "Usage: dictcli <build|lookup|related|stats> [args]"))))
