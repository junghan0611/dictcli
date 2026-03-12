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

### SQLite 스키마

```sql
-- 용어
CREATE TABLE terms (
  id INTEGER PRIMARY KEY,
  word TEXT NOT NULL,
  lang TEXT NOT NULL,        -- 'ko', 'en', 'de'
  definition TEXT,
  source TEXT,               -- 파일 경로 또는 소스명
  domain TEXT                -- 'philosophy', 'physics', 'general'...
);

-- 매핑 (한↔영↔독)
CREATE TABLE mappings (
  word_id INTEGER REFERENCES terms(id),
  target_word TEXT NOT NULL,
  target_lang TEXT NOT NULL,
  my_choice BOOLEAN DEFAULT FALSE  -- 내가 선택한 번역어
);

-- 동시출현 (연상맵)
CREATE TABLE cooccur (
  word1 TEXT NOT NULL,
  word2 TEXT NOT NULL,
  count INTEGER NOT NULL,
  source TEXT                -- 'saiculture', 'denote'...
);

-- 빈도
CREATE TABLE freq (
  word TEXT NOT NULL,
  count INTEGER NOT NULL,
  source TEXT NOT NULL
);
```

## 파일 구조

```
dictcli/
├── deps.edn
├── flake.nix
├── README.md
├── AGENTS.md
├── src/dictcli/
│   ├── core.clj        # CLI 진입점 (build, lookup, related, guide)
│   ├── parser.clj      # <<용어>> :: 정의 파서
│   ├── wordmap.clj     # saiculture wordmap.json 파서
│   ├── denote.clj      # Denote 파일명에서 태그 추출
│   ├── db.clj          # SQLite 빌드/쿼리
│   └── search.clj      # lookup, related, suggest-tags
├── test/dictcli/
│   └── parser_test.clj
└── data/                # 테스트용 샘플 glossary
    └── sample.txt
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
