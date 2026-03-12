# dictcli — 개인 어휘 사전 CLI

한글로 사고하는 사람이 영어 태그를 쓸 때 발생하는 구조적 문제를 푸는 도구.

## 왜 만드는가

```
한글 "협력" → cooperation? collaboration? coordination?
영어 "flow" ← 몰입? 흐름? 플로우?
```

- 같은 개념에 다른 태그가 붙는다 (태그 분산)
- 에이전트가 태그를 선택할 근거가 없다 (추측)
- 노트 간 연결이 끊긴다 (세렌디피티 소실)
- 번역어 하나가 앎의 틀을 결정한다

## 아키텍처

```
소스 (사람이 읽고 쓰는 것)          인덱스 (CLI가 검색하는 것)
────────────────────────          ─────────────────────────
dict/*.txt (<<용어>> :: 정의)  →
saiculture/wordmap.json       →   dictcli.db (SQLite)
Denote 태그 (파일명)           →
```

소스는 [ten](https://git.sr.ht/~nobiot/ten) 형식 `<<용어>> :: 정의`를 기본으로 한다.
사람이 txt를 직접 읽고 편집하고, Emacs에서 ten이 fontify + xref로 하이라이트.
dictcli는 이 소스들을 SQLite로 빌드하여 CLI/에이전트에 검색 인터페이스 제공.

## 데이터 소스 (3축)

| 축 | 소스 | 언어 | 규모 | 역할 |
|-----|------|------|------|------|
| 1 | `~/sync/org/dict/*.txt` | 한↔영 | 16,639 용어 | 번역 매핑, 정의 |
| 2 | `~/repos/gh/naver-saiculture/wordmap.json` | 한↔한(연상), 한↔독 | 48,872 단어 | 빈도, 동시출현, 중력장 |
| 3 | Denote 태그 (`~/org/` 파일명) | 영어 | 2,213 태그 | controlled vocabulary |

## ten 형식 (glossary)

```
<<현존재>> :: Dasein. 하이데거의 핵심 개념. 세계-내-존재.
<<Dasein>> :: 현존재

<<선험적>> :: transcendental. 경험의 가능 조건에 관한 것. (내 선택: "초월적" 아닌 "선험적")
<<transcendental>> :: 선험적
```

- 한 줄에 하나의 용어 (`<<용어>> :: 정의`)
- 양방향 매핑은 두 줄로 (한→영, 영→한)
- Denote 호환 파일명: `YYYYMMDDTHHMMSS--제목__glossary.txt`
- Emacs에서 ten이 fontify + `M-.`로 정의 점프

## 사용법 (구상)

```bash
# SQLite 인덱스 빌드
clj -M:run build --sources ~/sync/org/dict/ ~/repos/gh/naver-saiculture/wordmap.json

# 용어 검색
clj -M:run lookup "존재"
#  ko: 존재
#  en: being, existence (dict/philosophy)
#  de: Sein, Dasein (saiculture/존재와시간)
#  freq: 83 (saiculture)
#  cooccur: 하이데거(35), 시간(24), 진리(31)

# 역방향 검색
clj -M:run lookup "transcendental"
#  de: transzendental
#  ko: 선험적 (내 선택), 초월적 (일반)

# 연관 단어
clj -M:run related "하이데거"
#  칸트(65), 아리스토텔레스(54), 후설(44), 성스러움(45)
```

## 설치

### 바이너리 (권장)

GraalVM native-image로 빌드된 단일 실행 파일. JVM 불필요.

```bash
# 릴리즈에서 다운로드 (아키텍처별)
# dictcli-linux-x86_64
# dictcli-linux-aarch64

# 또는 소스에서 직접 빌드
nix develop
./run.sh native-build
# → target/dictcli (단일 바이너리, ~48MB)

cp target/dictcli ~/.local/bin/
```

| 아키텍처 | 빌드 방법 | 비고 |
|----------|-----------|------|
| x86_64 | 로컬 또는 CI | NixOS 노트북/NUC |
| aarch64 | 타겟 머신에서 빌드 | Oracle VM 등 ARM 서버 |

> GraalVM native-image는 크로스 컴파일 미지원.
> aarch64 바이너리는 aarch64 머신에서 `./run.sh native-build` 실행.

### 성능

| | JVM | Native binary |
|---|---|---|
| 시작 | ~1.7초 | **0.06초** |
| lookup | ~1.7초 | **0.06초** |
| build (48,872건) | ~2.7초 | ~2초 |
| 의존성 | JDK 필요 | **없음 (단일 파일)** |

## 기술 스택

- **Clojure** + **GraalVM native-image** — 단일 바이너리 CLI
- **SQLite** — 인덱스 DB
- **ten 형식** — glossary 소스 포맷
- **NixOS** — 개발 환경 (flake.nix)

## 관련 프로젝트

| 도구 | 데이터 | 언어 | 역할 |
|------|--------|------|------|
| bibcli | .bib (8,000+) | Go | 서지 검색 |
| denotecli | .org 파일명 (3,000+) | Go | 노트 검색 |
| lifetract | .db (Samsung Health) | Go | 건강 데이터 |
| gitcli | .git (14,000+ commits) | Go | 커밋 타임라인 |
| **dictcli** | **glossary + wordmap** | **Clojure** | **어휘 사전** |

## 철학

> 번역어 하나를 정하는 것이 앎의 틀을 결정한다.
> LLM마다 번역이 다른 것도 같은 문제 —
> 에이전트가 "통각"이라 쓰면 내 그물망에서 끊기고,
> "자의식"이라 쓰면 연결된다.

500워드 가이드의 본질: 에이전트에게 "내 단어"를 공유하는 것. 기술이 아니라 약속.

## 봇로그

- [20260309T194058](https://notes.junghanacs.com/botlog/20260309T194058) — 태그 정규화와 개인 어휘 사전 구상 (3축 구조, 중력장, 번역어 문제)
