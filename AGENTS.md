# dictcli AGENT 가이드

## 프로젝트 개요

- **리포지토리**: junghan0611/dictcli
- **목적**: 개인 어휘 그래프 — 한↔영 크로스링귀얼 쿼리 확장 + 한국어 형태소 분석
- **언어**: Clojure
- **데이터 포맷**: EDN 트리플 그래프 (`graph.edn`)

## 아키텍처 — 이중 실행 모드

```
dictcli
├─ Native Binary (GraalVM)     → expand, graph, stats, validate, ... (~9ms)
│   └─ core.clj (-main)        → gen-class, stem 코드 없음
│
└─ JVM Mode (clj + Kiwi JNI)   → stem only (~2.5s 시작)
    └─ stem_main.clj (-main)   → --batch, --serve, 단건
```

- **Native binary**: 에이전트가 knowledge_search 쿼리 확장 시 호출. 9ms.
- **JVM stem**: andenken 인덱싱 파이프라인에서 배치 호출. Kiwi JNI가 native-image 불가.
- **core.clj에 stem 관련 코드를 넣지 말 것.** stem은 stem_main.clj에서만.

## 핵심 개념

### 데이터 모델 — EDN 트리플 그래프

코드가 곧 데이터. `graph.edn` 하나.

```clojure
;; 트리플: [entity relation value]
["보편" :trans "universal"]
["보편" :opposite "특수"]
["보편" :source "20250424T233558"]
```

관계 타입:
| 관계 | 의미 |
|---|---|
| `:trans` | 번역/대응 (한↔영) |
| `:opposite` | 대극/반대 |
| `:related` | 의미 연결 |
| `:synonym` | 동의/유사 (같은 언어 내) |
| `:broader` | 상위 개념 |
| `:narrower` | 하위 개념 |
| `:domain` | 소속 영역 |
| `:source` | 출처 메타노트 ID |

### 트리플 인바리언트 (반드시 준수)

**단어 정책: 단어는 하나의 '개념'이다.**
- 문장이 아니다. 구절이 아니다. 설명이 아니다.
- 한글 entity: Denote 타이틀에 단어로 박을 수 있는 것 (조사 빼고)
- 영어 :trans value: Denote 영어 태그로 넣을 수 있는 것 `[a-z0-9]`
- **개념이 오염되면 1,2,3층 전부 무너진다.**

graph.edn의 모든 `[entity relation value]`는 아래를 만족해야 함:

1. **entity**: 하나의 개념 단어. 공백/구두점/숫자시작/특수문자 불허.
2. **relation**: 키워드. 위 8개만 허용.
3. **value**:
   - `:trans` → **`[a-z0-9]` 소문자+숫자만**. 공백/하이픈/대문자/한글/설명문 불허. **단어만**.
   - `:source` → Denote identifier (`YYYYMMDDTHHMMSS`)
   - 그 외 → 하나의 개념 단어
4. **중복 금지**: 동일 `[entity relation value]` 2회 이상 불허.
5. **대칭 관계 자동**: `:opposite`, `:synonym`, `:related` → 역방향 자동 추가.
6. **최대 길이**: value 50자 이하.

검증: `clj -M:run validate` (모든 커밋 전 실행)

## 파일 구조

```
dictcli/
├── deps.edn
├── build.clj                  # uberjar 빌드 (clj -T:build uber)
├── flake.nix
├── graph.edn                  # 트리플 그래프 (진실의 원천)
├── run.sh                     # CLI 래퍼 (native/JVM 분기)
├── src/dictcli/
│   ├── core.clj               # CLI 진입점 — expand, graph, stats, validate
│   ├── graph.clj              # EDN 트리플 스토어, 인덱스, 쿼리
│   ├── normalize.clj          # 정규화 (소문자, 공백 제거, 복합어 분리)
│   ├── validate.clj           # 인바리언트 검증
│   ├── stem.clj               # Kiwi 형태소 분석 (JVM 전용)
│   ├── stem_main.clj          # stem CLI 엔트리포인트 (--batch, --serve)
│   └── stem_server.clj        # stem 소켓 서버
├── src-legacy/                # [레거시] SQLite/ten 파서 — 사용 안 함
├── test/dictcli/
│   └── graph_test.clj         # 트리플 테스트
├── lib/                       # Kiwi JNI jar (stem-setup으로 다운로드)
├── models/                    # Kiwi 모델 (105MB, stem-setup으로 다운로드)
└── data/                      # 시드/소스 EDN
    ├── seed-clusters.edn      # 수작업 클러스터
    ├── syntopicon.edn         # 신토피콘 102 Great Ideas
    ├── philosophy.edn         # philosophy glossary
    ├── general.edn            # general glossary
    ├── practical.edn          # 실무 용어
    ├── meta-sources.edn       # meta 노트 :source 연결
    ├── meta-harvest-1.edn     # meta 수확 1차
    ├── meta-harvest-2.edn     # meta 수확 2차
    └── kengdic-coverage.edn   # kengdic 커버리지
```

## 빌드 & 실행

```bash
# 개발 (clj 직접)
clj -M:run expand "보편" --json   # 쿼리 확장
clj -M:run validate               # 인바리언트 검증
clj -M:run stats                   # 그래프 통계
clj -M:test                        # 테스트

# stem (JVM + Kiwi)
./run.sh stem-setup                # Kiwi jar + 모델 다운로드 (최초 1회)
./run.sh stem "설계했다"            # 단건 어간 추출
echo -e "문장1\n문장2" | ./run.sh stem --batch  # 배치 (andenken용)

# native binary 빌드 (GraalVM 필요)
./run.sh build                     # target/dictcli-{arch}
./run.sh build --output /path/to   # 빌드 + 복사 (graph.edn 포함)
```

### 빌드 주의사항

- `./run.sh build`는 `target/dictcli-{arch}` 캐시를 확인 — 있으면 복사만
- 소스 수정 후 반드시 `rm -rf target/` 또는 `--force`
- `build.clj`의 `ns-compile`에 stem 관련 ns 넣지 말 것 (JNI 의존)
- Clojure 수정 후 괄호 균형 검증 필수: `(bound? #'dictcli.core/-main)` 테스트

### 커밋 메시지 스타일

```
feat: add ten glossary parser
fix: handle multi-line definitions
data: add philosophy glossary sample
```

## 3층 모델에서의 위치

| 층 | 도구 | 역할 |
|----|------|------|
| 1층 | knowledge_search (andenken) | 임베딩 벡터 검색 |
| 2층 | denotecli + dblock | 정확 매칭 + 그래프 링크 |
| **3층** | **dictcli expand + stem** | **한→영 쿼리 확장 + 한국어 어간 추출** |

## 봇로그 참조

- [20260309T194058](https://notes.junghanacs.com/botlog/20260309T194058) — 태그 정규화와 개인 어휘 사전 구상
- [20260308T091235](https://notes.junghanacs.com/botlog/20260308T091235) — dblock 링크 포맷 정책
- [20260304T004500](https://notes.junghanacs.com/botlog/20260304T004500) — 지식그래프 무무 무의식 에이전트 연상맵

## 철학

> 500워드는 벽이 아니라 '중력장(마당)'이다.
> 에이전트의 방대한 잠재 공간을 정한님의 세계관으로 끌어당기는 핵심 마당.
> — 제미나이(glg)

> 번역어 하나를 정하는 것이 앎의 틀을 결정한다.
> 에이전트에게 "내 단어"를 공유하는 것. 기술이 아니라 약속.
