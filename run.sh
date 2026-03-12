#!/usr/bin/env bash
# dictcli — 커맨드 모음
set -euo pipefail
cd "$(dirname "$0")"

CMD="${1:-help}"
shift 2>/dev/null || true

case "$CMD" in
  build)
    echo "=== 빌드: glossary + wordmap → SQLite ==="
    rm -f dictcli.db
    clj -M:run build "$@"
    ;;
  build-sample)
    echo "=== 샘플 빌드: data/ → SQLite ==="
    rm -f dictcli.db
    clj -M:run build data/
    ;;
  lookup)
    clj -M:run lookup "$@"
    ;;
  related)
    clj -M:run related "$@"
    ;;
  stats)
    clj -M:run stats
    ;;
  test)
    echo "=== 테스트 ==="
    clj -M:test
    ;;
  repl)
    echo "=== REPL ==="
    clj -M:repl
    ;;
  clean)
    echo "=== 정리 ==="
    rm -f dictcli.db
    rm -rf .cpcache/
    echo "done"
    ;;
  help|*)
    echo "dictcli — 개인 어휘 사전 CLI"
    echo ""
    echo "Usage: ./run.sh <command> [args]"
    echo ""
    echo "Commands:"
    echo "  build          전체 빌드 (~/sync/org/dict + saiculture wordmap)"
    echo "  build-sample   샘플 빌드 (data/ 폴더만)"
    echo "  lookup <word>  용어 검색"
    echo "  related <word> 연관 단어"
    echo "  stats          DB 통계"
    echo "  test           테스트 실행"
    echo "  repl           Clojure REPL"
    echo "  clean          DB, 캐시 삭제"
    ;;
esac
