# dictcli AGENT 가이드

## 프로젝트 개요

- **리포지토리**: junghan0611/dictcli
- **목적**: 개인 어휘 사전 CLI — 한↔영↔독 매핑, 연상맵, 에이전트 태그 가이드
- **언어**: Clojure
- **데이터 포맷**: ten 형식 `<<용어>> :: 정의` (txt) → SQLite 인덱스

## 핵심 개념

### 데이터 흐름

```
소스 (txt, json)  →  dictcli build  →  dictcli.db (SQLite)  →  dictcli lookup/related
                                                              →  pi-skill (에이전트)
                                                              →  ten (Emacs fontify)
```

### 3축 데이터 소스

| 축 | 경로 | 형식 | 내용 |
|-----|------|------|------|
| 1 | `~/sync/org/dict/*.txt` | ten glossary | 한↔영 매핑 16,639 용어 |
| 2 | `~/repos/gh/naver-saiculture/wordmap.json` | JSON | 빈도 48,872 + 동시출현 100쌍 |
| 3 | `~/org/` Denote 파일명 | 파일명 파싱 | 영어 태그 2,213종 |

### ten 형식 규칙

```
<<용어>> :: 정의 또는 번역어
```

- 한 줄에 하나의 용어
- `<<` `>>` 로 용어를 감싼다
- `::` 뒤에 정의/번역
- 양방향 매핑은 두 줄로 작성
- 파일 확장자: `.txt` (ten 호환)
- 파일명: Denote 형식 `YYYYMMDDTHHMMSS--제목__glossary.txt`

### 데이터 모델 — EDN 트리플 그래프

코드가 곧 데이터. SQLite가 아니라 `graph.edn` 하나.

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
├── flake.nix
├── graph.edn              # 트리플 그래프 (진실의 원천)
├── src/dictcli/
│   ├── core.clj           # CLI 진입점 (add, graph, expand, validate...)
│   ├── graph.clj          # EDN 트리플 스토어, 인덱스, 쿼리
│   ├── normalize.clj      # 정규화 (소문자, 공백 제거, 복합어 분리)
│   ├── parser.clj         # <<용어>> :: 정의 ten 파서
│   ├── db.clj             # [레거시] SQLite
│   └── wordmap.clj        # saiculture wordmap 파서
├── test/dictcli/
│   ├── graph_test.clj     # 트리플 테스트
│   └── parser_test.clj    # ten 파서 테스트
└── data/                   # 시드/소스 EDN
    ├── seed-clusters.edn   # 수작업 4개 클러스터
    ├── syntopicon.edn      # 신토피콘 한↔영
    ├── general.edn         # general glossary
    └── meta-sources.edn    # meta 노트 :source 연결
```

## 개발 가이드

### 빌드 & 실행

```bash
nix develop              # 개발 환경 진입
clj -M:run build         # 소스 → SQLite 빌드
clj -M:run lookup "존재"  # 용어 검색
clj -M:test              # 테스트
```

### 구현 순서 (제안)

1. **parser.clj** — `<<용어>> :: 정의` 파서 (가장 먼저, 테스트와 함께)
2. **db.clj** — SQLite 스키마 생성, 삽입
3. **core.clj** — CLI 진입점 (build 커맨드)
4. **wordmap.clj** — saiculture wordmap.json 파서
5. **search.clj** — lookup, related
6. **denote.clj** — Denote 태그 추출 (denotecli 연동 또는 직접 파싱)

### 커밋 메시지 스타일

```
feat: add ten glossary parser
fix: handle multi-line definitions
data: add philosophy glossary sample
```

## 봇로그 참조

- [20260309T194058](https://notes.junghanacs.com/botlog/20260309T194058) — 태그 정규화와 개인 어휘 사전 구상 (핵심 문서, 3축 구조, 중력장, 번역어, org-supertag 검토)
- [20260308T091235](https://notes.junghanacs.com/botlog/20260308T091235) — dblock 링크 포맷 정책 (태그 정책 `[a-z0-9]` only)
- [20260304T004500](https://notes.junghanacs.com/botlog/20260304T004500) — 지식그래프 무무 무의식 에이전트 연상맵
- [20260305T055000](https://notes.junghanacs.com/botlog/20260305T055000) — 오픈클로 유즈케이스와 어쏠로지스트의 길

## 관련 소스

- **이기상 선생님**: `~/repos/gh/naver-saiculture/` — 3,074편 블로그, wordmap.json
- **dict/ glossary**: `~/sync/org/dict/` — ten 형식 용어 사전
- **ten 패키지**: `~/.local/straight/repos/ten/` — Emacs fontify + xref
- **코디정 번역어**: `denote:20241009T113329` — 순수이성비판 38개 핵심어 재번역

## 철학

> 500워드는 벽이 아니라 '중력장(마당)'이다.
> 에이전트의 방대한 잠재 공간을 정한님의 세계관으로 끌어당기는 핵심 마당.
> — 제미나이(glg)

> 번역어 하나를 정하는 것이 앎의 틀을 결정한다.
> 에이전트에게 "내 단어"를 공유하는 것. 기술이 아니라 약속.
