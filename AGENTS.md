# dictcli AGENT Guide

## Project Overview

- **Repository**: junghan0611/dictcli
- **Purpose**: Personal vocabulary graph — Korean↔English cross-lingual query expansion + Korean morphological analysis
- **Language**: Clojure
- **Data format**: EDN triple graph (`graph.edn`, ~3,972 triples)

## Architecture — Dual Execution Mode

```
dictcli
├─ Native Binary (GraalVM)     → expand, graph, stats, validate  (~9ms)
│   └─ core.clj (-main)        → no stem code
│
└─ JVM Mode (clj + Kiwi JNI)   → stem only (~2.5s startup)
    └─ stem_main.clj (-main)   → --batch, --serve, single
```

- **Native binary**: called by agents during knowledge_search query expansion. 9ms cold start.
- **JVM stem**: batch invocation from the andenken indexing pipeline. Kiwi JNI is incompatible with native-image.
- **Never mix**: do not put stem-related code in `core.clj`. Stem lives in `stem_main.clj` only.

## Core Concepts

### Data Model — EDN Triple Graph

Code is data. Single source of truth: `graph.edn`.

```clojure
;; Triple: [entity relation value]
["보편" :trans "universal"]
["보편" :opposite "특수"]
["보편" :source "20250424T233558"]
```

| Relation | Meaning |
|---|---|
| `:trans` | Translation/mapping (Korean↔English) |
| `:opposite` | Antonym / polar opposite |
| `:related` | Semantic connection |
| `:synonym` | Same-language near-synonym |
| `:broader` | Hypernym |
| `:narrower` | Hyponym |
| `:domain` | Domain / field |
| `:source` | Denote note ID (origin) |

### Triple Invariants (must be respected)

**Word policy: a word is a single concept.**
- Not a sentence. Not a phrase. Not an explanation.
- Korean entity: something usable as a Denote title word (no particles)
- English `:trans` value: something usable as a Denote English tag `[a-z0-9]`
- **Contaminating a concept collapses all three layers.**

All `[entity relation value]` in graph.edn must satisfy:

1. **entity**: one concept word — no spaces, punctuation, digit-leading, or special chars
2. **relation**: keyword — only the 8 types above
3. **value**:
   - `:trans` → **`[a-z0-9]` lowercase+digits only**. No spaces, hyphens, uppercase, Korean, or explanations. **One word.**
   - `:source` → Denote identifier (`YYYYMMDDTHHMMSS`)
   - others → one concept word
4. **No duplicates**: same `[entity relation value]` must not appear twice
5. **Auto-symmetric**: `:opposite`, `:synonym`, `:related` → reverse triple auto-added
6. **Max length**: value ≤ 50 chars

Validate: `clj -M:run validate` (run before every commit)

### Data Quality Policy

- Fix obvious typos and incomplete values immediately
- French/non-English words: leave unless clearly wrong (may be intentional)
- Non-concept words: leave unless they violate invariants
- Run `validate` after any graph.edn change to catch regressions

## File Structure

```
dictcli/
├── deps.edn
├── build.clj                  # uberjar build (clj -T:build uber)
├── flake.nix
├── graph.edn                  # triple graph (source of truth)
├── run.sh                     # CLI wrapper (native/JVM dispatch)
├── src/dictcli/
│   ├── core.clj               # CLI entrypoint — expand, graph, stats, validate
│   ├── graph.clj              # EDN triple store, index, query
│   ├── normalize.clj          # normalization (lowercase, trim, compound split)
│   ├── validate.clj           # invariant validation
│   ├── stem.clj               # Kiwi morphological analysis (JVM only)
│   ├── stem_main.clj          # stem CLI entrypoint (--batch, --serve)
│   └── stem_server.clj        # stem socket server
├── src-legacy/                # [legacy] SQLite/ten parser — not used
├── test/dictcli/
│   └── graph_test.clj         # triple tests
├── lib/                       # Kiwi JNI jar (downloaded by stem-setup)
├── models/                    # Kiwi models (105MB, downloaded by stem-setup)
└── data/                      # seed/source EDN files
    ├── seed-clusters.edn
    ├── syntopicon.edn
    ├── philosophy.edn
    ├── general.edn
    ├── practical.edn
    ├── meta-sources.edn
    ├── meta-harvest-1.edn
    ├── meta-harvest-2.edn
    └── kengdic-coverage.edn
```

## Build & Run

```bash
# Development (direct clj)
clj -M:run expand "보편" --json   # query expansion
clj -M:run validate               # invariant validation
clj -M:run stats                  # graph statistics
clj -M:test                       # tests

# Stem (JVM + Kiwi)
./run.sh stem-setup               # download Kiwi jar + models (one-time)
./run.sh stem "설계했다"           # single morphological analysis
echo -e "line1\nline2" | ./run.sh stem --batch   # batch (for andenken)

# Native binary build (GraalVM required)
./run.sh build                    # → target/dictcli-{arch}
./run.sh build --output /path/to  # build + copy (includes graph.edn)
```

### Build Notes

- `./run.sh build` checks cache at `target/dictcli-{arch}` via mtime validation
  - Cache hit + valid → skip build (copy only)
  - Cache invalid → delete + rebuild
- After modifying source: cache auto-invalidated; or `rm -rf target/`
- Do not add stem-related namespaces to `build.clj` ns-compile (JNI dependency)
- **NixOS**: patchelf is automatically skipped (`/etc/NIXOS` guard) — do not patch interpreter on NixOS
- After build: smoke test runs; if it fails, `--output` copy is skipped
- Bracket balance is critical in `core.clj` — always verify `(-main)` is reachable

### Commit Message Style

```
feat: add ten glossary parser
fix: handle multi-line definitions
data: add philosophy glossary sample
fix(data): correct typos in graph.edn
```

## Position in the 3-Layer Model

| Layer | Tool | Role |
|---|---|---|
| 1 | knowledge_search (andenken) | embedding vector search |
| 2 | denotecli + dblock | exact match + graph links |
| **3** | **dictcli expand + stem** | **Korean→English query expansion + morphological stemming** |

### Integration with andenken and agent-config (important)

`dictcli` is not maintained for standalone demo quality alone.
Its practical value is judged by whether it improves **real andenken retrieval** for fresh-starting agents.

Operational facts to remember:

- andenken already covers many cross-lingual cases via embeddings
- dictcli should therefore act as a **precision correction layer**, not a brute-force vocabulary dump
- current andenken query expansion extracts **Korean tokens** and sends them to `dictcli expand`
- this means transliteration mappings can matter immediately for search quality (e.g. `조테로 → zotero`, `안덴켄 → andenken`)
- multi-word names may need an invariant-safe workaround because graph entities cannot contain spaces
  - prefer one-word canonical entities (e.g. `바네바부시`, `vannevarbush`)
  - then add small helper links only when needed for actual tokenized queries (e.g. `바네바 :related 바네바부시`)
- do not add broad or ornamental transliterations unless they measurably help retrieval

### Propagation rule

A local `graph.edn` edit is not enough if agents consume dictcli through the agent-config skill bundle.
After dictcli data changes, remember the downstream path:

1. validate / test in this repo
2. rebuild or recopy the skill bundle so the updated `graph.edn` reaches `agent-config/skills/dictcli/`
3. if needed, run `~/repos/gh/agent-config/run.sh setup:build && ~/repos/gh/agent-config/run.sh setup:links`
4. then verify the effect from andenken-facing queries, not only from raw `dictcli expand`

### Evaluation stance

Prefer small, practical additions with before/after checks.
A good dictcli change should be explainable like this:

- what real query failed before?
- what exact triples were added?
- what changed in `expand` output?
- did the change improve actual andenken retrieval?

## Botlog References

- [20260309T194058](https://notes.junghanacs.com/botlog/20260309T194058) — tag normalization and personal vocabulary graph concept
- [20260308T091235](https://notes.junghanacs.com/botlog/20260308T091235) — dblock link format policy
- [20260304T004500](https://notes.junghanacs.com/botlog/20260304T004500) — knowledge graph unconscious agent association map

## Philosophy

> The 500-word guide is not a wall but a gravity field (마당):
> it pulls the agent's vast latent space toward the user's worldview.
> — Gemini (GLG)

> Choosing a single translation defines the shape of knowledge.
> Sharing "my words" with an agent is not a technical act — it is a covenant.
