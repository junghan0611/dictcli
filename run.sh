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
  stem)
    # Kiwi JNI 필요 → JVM 전용 (native-image 불가)
    # 단건: ./run.sh stem "문장" [--tokens] [--json]
    # 배치: echo -e "문장1\n문장2" | ./run.sh stem --batch
    # 서버: ./run.sh stem --serve [port]  (쿼리당 ~1ms)
    KIWI_JAR="lib/kiwi-java-v0.23.0-lnx-x86-64.jar"
    [ "$ARCH" = "aarch64" ] && KIWI_JAR="lib/kiwi-java-v0.23.0-lnx-aarch64.jar"
    if [ ! -f "$KIWI_JAR" ]; then
      echo "❌ $KIWI_JAR 없음. ./run.sh stem-setup 으로 다운로드"
      exit 1
    fi
    clj -Sdeps "{:deps {kr.pe.bab2min/kiwi-java {:local/root \"$KIWI_JAR\"}}}" \
      -M -m dictcli.stem-main "$@"
    ;;
  stem-setup)
    # Kiwi jar + 모델 다운로드 (아키텍처 자동 감지)
    KIWI_VER="v0.23.0"
    case "$ARCH" in
      x86_64)  KIWI_SUFFIX="lnx-x86-64" ;;
      aarch64) KIWI_SUFFIX="lnx-aarch64" ;;
      *)       echo "❌ 지원 안 하는 아키텍처: $ARCH"; exit 1 ;;
    esac
    mkdir -p lib models
    KIWI_JAR="lib/kiwi-java-${KIWI_VER}-${KIWI_SUFFIX}.jar"
    if [ ! -f "$KIWI_JAR" ]; then
      echo "⬇️  KiwiJava jar ($KIWI_SUFFIX)..."
      curl -sL -o "$KIWI_JAR" \
        "https://github.com/bab2min/Kiwi/releases/download/${KIWI_VER}/kiwi-java-${KIWI_VER}-${KIWI_SUFFIX}.jar"
      echo "  ✅ $KIWI_JAR ($(du -h "$KIWI_JAR" | cut -f1))"
    else
      echo "✅ jar 이미 있음: $KIWI_JAR"
    fi
    if [ ! -d "models/models" ]; then
      echo "⬇️  Kiwi 모델 (105MB)..."
      curl -sL "https://github.com/bab2min/Kiwi/releases/download/${KIWI_VER}/kiwi_model_${KIWI_VER}_base.tgz" \
        | tar -xzf - -C models/
      echo "  ✅ models/ ($(du -sh models/ | cut -f1))"
    else
      echo "✅ 모델 이미 있음: models/"
    fi
    echo ""
    echo "테스트: ./run.sh stem \"설계했다\""
    ;;
  init)
    echo "=== 시드 데이터 임포트 ==="
    rm -f graph.edn
    clj -M:run import data/seed-clusters.edn
    ;;
  rebuild)
    echo "=== graph.edn 재구성 (모든 data/*.edn) ==="
    rm -f graph.edn
    # 순서: 시드 → 신토피콘 → 일반 → 소스 → 철학 → 실무 → harvest-1 → harvest-2 → kengdic
    for edn in \
      data/seed-clusters.edn \
      data/syntopicon.edn \
      data/general.edn \
      data/meta-sources.edn \
      data/philosophy.edn \
      data/practical.edn \
      data/meta-harvest-1.edn \
      data/meta-harvest-2.edn \
      data/kengdic-coverage.edn; do
      if [ -f "$edn" ]; then
        echo "  → $edn"
        clj -M:run import "$edn"
      fi
    done
    echo ""
    echo "=== 검증 ==="
    clj -M:run validate
    echo ""
    clj -M:run stats
    ;;

  ## --- 빌드 ---
  build)
    # 표준 빌드 커맨드 — agent-config에서 Go CLI와 동일하게 호출
    # Usage: ./run.sh build [--output /path/to/binary] [--force]
    OUTPUT=""
    FORCE=false
    ARGS=("$@")
    i=0
    while [ $i -lt ${#ARGS[@]} ]; do
      case "${ARGS[$i]}" in
        --output) i=$((i+1)); OUTPUT="${ARGS[$i]:-}" ;;
        --force)  FORCE=true ;;
        *)        [ -z "$OUTPUT" ] && OUTPUT="${ARGS[$i]}" ;;
      esac
      i=$((i+1))
    done

    # 캐시 확인 — force가 아니고 바이너리가 있으면 복사만
    if [ "$FORCE" = false ] && [ -f "${BINARY}" ]; then
      echo "✅ 캐시 사용: ${BINARY}"
    else
      # nix develop 안에서 빌드 (native-image 필요)
      # FHS 환경에서 빌드 — 바이너리가 표준 경로에 링크됨 (Docker 호환)
      NI_ARGS="--initialize-at-build-time --no-fallback -H:+ReportExceptionStackTraces"
      NI_ARGS="$NI_ARGS -H:Name=dictcli-${ARCH} -jar target/dictcli.jar -o ${BINARY}"

      if command -v native-image &>/dev/null; then
        echo "=== GraalVM native-image 빌드 (${ARCH}) ==="
        clj -T:build uber
        # shellcheck disable=SC2086
        native-image $NI_ARGS
      else
        # FHS 환경 사용 — 바이너리가 /lib64/ld-linux... 표준 링크
        FHS_BIN="$(nix build .#fhs --no-link --print-out-paths 2>/dev/null)/bin/dictcli-build"
        if [ -x "$FHS_BIN" ]; then
          echo "=== FHS 환경 → native-image 빌드 (${ARCH}) ==="
          "$FHS_BIN" -c "cd $(pwd) && clj -T:build uber && native-image $NI_ARGS"
        else
          echo "=== nix develop → native-image 빌드 (${ARCH}) ==="
          nix develop --command bash -c "cd $(pwd) && clj -T:build uber && native-image $NI_ARGS"
        fi
      fi
      # patchelf — Nix store 경로 → 표준 경로 (Docker/non-NixOS 호환)
      if command -v patchelf &>/dev/null; then
        INTERP="/lib64/ld-linux-x86-64.so.2"
        [ "$ARCH" = "aarch64" ] && INTERP="/lib/ld-linux-aarch64.so.1"
        patchelf --set-interpreter "$INTERP" "${BINARY}" 2>/dev/null || true
        patchelf --remove-rpath "${BINARY}" 2>/dev/null || true
      elif nix develop --command patchelf --version &>/dev/null 2>&1; then
        nix develop --command bash -c "
          INTERP='/lib64/ld-linux-x86-64.so.2'
          [ '${ARCH}' = 'aarch64' ] && INTERP='/lib/ld-linux-aarch64.so.1'
          patchelf --set-interpreter \"\$INTERP\" '${BINARY}' 2>/dev/null || true
          patchelf --remove-rpath '${BINARY}' 2>/dev/null || true
        "
      fi
      echo "  ✅ ${BINARY} ($(du -h "${BINARY}" | cut -f1))"
    fi

    # 스모크 테스트
    DICTCLI_GRAPH=graph.edn "${BINARY}" validate 2>&1 | tail -1

    # --output 지정 시 바이너리 + graph.edn 세트 복사
    if [ -n "$OUTPUT" ]; then
      cp "${BINARY}" "$OUTPUT"
      # graph.edn도 같은 디렉토리에 동봉 (바이너리가 CWD의 graph.edn에 의존)
      OUTPUT_DIR="$(dirname "$OUTPUT")"
      if [ -f "graph.edn" ]; then
        cp graph.edn "$OUTPUT_DIR/graph.edn"
        echo "→ $OUTPUT + graph.edn"
      else
        echo "→ $OUTPUT (⚠️ graph.edn 없음)"
      fi
    fi
    ;;

  native-build)
    # 직접 빌드 (nix develop 안에서 호출)
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
    echo "  stem <\"문장\"> [--tokens]      Kiwi 어간 추출 + expand (JVM)"
    echo "  init                         시드 데이터로 초기화"
    echo "  rebuild                      graph.edn 재구성 (모든 data/*.edn)"
    echo ""
    echo "Build:"
    echo "  build [--output PATH] [--force]  표준 빌드 (FHS, Docker 호환)"
    echo "  native-build                     GraalVM 직접 (nix develop 안에서)"
    echo "  jar-build                        JVM uberjar"
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
