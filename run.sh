#!/usr/bin/env bash
# dictcli — 힣의 어휘 연결체
set -euo pipefail
cd "$(dirname "$0")"

CMD="${1:-help}"
shift 2>/dev/null || true

# 아키텍처 감지
ARCH=$(uname -m)  # aarch64 / x86_64
BINARY="target/dictcli-${ARCH}"

case "$CMD" in
  ## --- 핵심 커맨드 (EDN 트리플 그래프) ---
  add)
    clj -M:run add "$@"
    ;;
  graph)
    clj -M:run graph "$@"
    ;;
  expand)
    clj -M:run expand "$@"
    ;;
  cluster)
    clj -M:run cluster "$@"
    ;;
  stats)
    clj -M:run stats
    ;;
  import)
    clj -M:run import "$@"
    ;;
  validate)
    clj -M:run validate
    ;;
  normalize)
    clj -M:run normalize
    ;;
  init)
    echo "=== 시드 데이터 임포트 ==="
    rm -f graph.edn
    clj -M:run import data/seed-clusters.edn
    ;;

  ## --- 빌드 ---
  native-build)
    echo "=== GraalVM native-image 빌드 (${ARCH}) ==="

    # 1. uberjar
    echo "→ uberjar 빌드..."
    clj -T:build uber
    echo "  ✅ target/dictcli.jar"

    # 2. native-image
    echo "→ native-image 컴파일..."
    native-image \
      --initialize-at-build-time \
      --no-fallback \
      -H:+ReportExceptionStackTraces \
      -H:Name="dictcli-${ARCH}" \
      -jar target/dictcli.jar \
      -o "${BINARY}"

    echo "  ✅ ${BINARY}"
    ls -lh "${BINARY}"

    # 3. 스모크 테스트
    echo ""
    echo "→ 스모크 테스트:"
    DICTCLI_GRAPH=graph.edn "${BINARY}" expand 보편
    ;;

  jar-build)
    echo "=== JVM uberjar 빌드 ==="
    clj -T:build uber
    echo "✅ target/dictcli.jar"
    echo "실행: java -jar target/dictcli.jar <command>"
    ;;

  ## --- 레거시 (SQLite, 필요시만) ---
  legacy-build)
    echo "=== [레거시] glossary + wordmap → SQLite ==="
    echo "  clj -M:legacy:run build 으로 실행"
    rm -f dictcli.db
    clj -M:legacy:run build "$@"
    ;;

  ## --- 개발 ---
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
    rm -rf .cpcache/ target/
    echo "done"
    ;;
  help|*)
    echo "dictcli — 힣의 어휘 연결체"
    echo ""
    echo "Usage: ./run.sh <command> [args]"
    echo ""
    echo "Graph (EDN 트리플):"
    echo "  add <entity> <rel> <value>   트리플 추가"
    echo "  graph <word>                 단어의 모든 연결"
    echo "  expand <word> [--json]       쿼리 확장 (한→영)"
    echo "  cluster <meta-id>            메타노트 클러스터"
    echo "  stats                        그래프 통계"
    echo "  import <file.edn>            시드 데이터 병합"
    echo "  validate                     인바리언트 검증"
    echo "  normalize                    정규화"
    echo "  init                         시드 데이터로 초기화"
    echo ""
    echo "Build:"
    echo "  native-build                 GraalVM native binary (${ARCH})"
    echo "  jar-build                    JVM uberjar (옵션)"
    echo ""
    echo "관계 타입:"
    echo "  :trans     번역/대응 (한↔영)"
    echo "  :opposite  대극/반대"
    echo "  :related   의미 연결"
    echo "  :synonym   동의/유사"
    echo "  :broader   상위 개념"
    echo "  :narrower  하위 개념"
    echo "  :domain    소속 영역"
    echo "  :source    출처 메타노트 ID"
    echo ""
    echo "Development:"
    echo "  test    테스트"
    echo "  repl    Clojure REPL"
    echo "  clean   정리"
    echo ""
    echo "환경변수:"
    echo "  DICTCLI_GRAPH  graph.edn 경로 (기본: CWD/graph.edn)"
    ;;
esac
