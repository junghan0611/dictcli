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
  ## --- Native Image ---
  uberjar)
    echo "=== Uberjar 빌드 ==="
    mkdir -p target
    clj -J-Dclojure.compiler.direct-linking=true -T:uberjar
    echo "→ target/dictcli.jar"
    ;;
  native-build)
    echo "=== GraalVM Native Image 빌드 ==="
    # 1. uberjar
    mkdir -p target
    clj -J-Dclojure.compiler.direct-linking=true -T:uberjar
    # 2. native-image
    native-image \
      -jar target/dictcli.jar \
      -o target/dictcli \
      -H:Name=dictcli \
      --no-fallback \
      --initialize-at-build-time \
      -H:+ReportExceptionStackTraces \
      --enable-native-access=ALL-UNNAMED \
      -J-Xmx4g \
      2>&1
    echo ""
    echo "→ target/dictcli ($(du -h target/dictcli | cut -f1))"
    echo "  ./target/dictcli lookup 존재"
    ;;
  native-run)
    # native binary로 실행
    ./target/dictcli "$@"
    ;;
  ## --- 관리 ---
  clean)
    echo "=== 정리 ==="
    rm -f dictcli.db
    rm -rf .cpcache/ target/
    echo "done"
    ;;
  help|*)
    echo "dictcli — 개인 어휘 사전 CLI"
    echo ""
    echo "Usage: ./run.sh <command> [args]"
    echo ""
    echo "Development (JVM):"
    echo "  build          전체 빌드 (~/sync/org/dict + saiculture wordmap)"
    echo "  build-sample   샘플 빌드 (data/ 폴더만)"
    echo "  lookup <word>  용어 검색"
    echo "  related <word> 연관 단어"
    echo "  stats          DB 통계"
    echo "  test           테스트 실행"
    echo "  repl           Clojure REPL"
    echo ""
    echo "Native (GraalVM):"
    echo "  uberjar        AOT uberjar 빌드"
    echo "  native-build   GraalVM native binary 빌드"
    echo "  native-run     native binary로 실행"
    echo ""
    echo "Management:"
    echo "  clean          DB, 캐시, target 삭제"
    echo ""
    echo "Shells:"
    echo "  nix develop          — GraalVM (native-image 포함)"
    echo "  nix develop .#jvm    — JVM only (가벼운 개발)"
    ;;
esac
