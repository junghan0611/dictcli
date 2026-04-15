# dictcli — Personal Vocabulary Graph

A tool for people who think in Korean but need to tag in English.

## Why

```
Korean "협력" → cooperation? collaboration? coordination?
English "flow" ← 몰입? 흐름? 플로우?
```

- The same concept gets different tags (tag fragmentation)
- Agents have no basis for choosing a translation (guesswork)
- Note connections break (serendipity lost)
- A single translation choice defines the shape of knowledge

## Architecture

### Dual Binary Model

```
dictcli
├─ Native Binary (GraalVM)     → expand, graph, stats, validate  (~9ms)
│   └─ core.clj (-main)        → no stem code
│
└─ JVM Mode (clj + Kiwi JNI)   → stem only (~2.5s startup)
    └─ stem_main.clj (-main)   → --batch, --serve, single
```

- **Native binary**: called by agents for knowledge_search query expansion. 9ms cold start.
- **JVM stem**: batch invocation from the andenken indexing pipeline. Kiwi JNI is incompatible with native-image.
- **Do not mix**: stem code must not appear in `core.clj`.

### Data Model — EDN Triple Graph

Code is data. Single source of truth: `graph.edn` (~3,972 triples).

```clojure
;; Triple: [entity relation value]
["보편" :trans "universal"]
["보편" :opposite "특수"]
["보편" :source "20250424T233558"]
```

| Relation | Meaning |
|----------|---------|
| `:trans` | Translation/mapping (Korean↔English) |
| `:opposite` | Antonym / polar opposite |
| `:related` | Semantic connection |
| `:synonym` | Same-language near-synonym |
| `:broader` | Hypernym |
| `:narrower` | Hyponym |
| `:domain` | Domain / field |
| `:source` | Denote note ID (origin) |

### Triple Invariants

**Word policy: a word is a single concept.**
- Not a sentence. Not a phrase. Not an explanation.
- Korean entity: something that can be a Denote title word (no particles)
- English `:trans` value: something that can be a Denote English tag `[a-z0-9]`

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

## Usage

```bash
# Query expansion (Korean → English tag candidates)
clj -M:run expand "보편"
# → universal, universalism, paideia

# JSON output (for agent consumption)
clj -M:run expand "협력" --json

# Validate all invariants
clj -M:run validate

# Graph statistics
clj -M:run stats

# Korean morphological analysis (JVM only)
./run.sh stem "설계했다"
# → 설계

# Batch stem (for andenken pipeline)
echo -e "문장1\n문장2" | ./run.sh stem --batch
```

## Installation

### Binary (recommended)

Single executable built with GraalVM native-image. No JVM required.

```bash
# Build from source (GraalVM required)
nix develop
./run.sh build
# → target/dictcli-linux-x86_64

# Copy to PATH
cp target/dictcli-linux-x86_64 ~/.local/bin/dictcli
```

| Architecture | How to build | Notes |
|---|---|---|
| x86_64 | local or CI | NixOS laptop / NUC |
| aarch64 | build on target machine | Oracle VM, ARM servers |

> GraalVM native-image does not support cross-compilation.
> Build aarch64 binary on an aarch64 machine.

### Build Notes

- `./run.sh build` checks cache at `target/dictcli-{arch}` — copies if valid, rebuilds if stale or missing
- After modifying source, cache is auto-invalidated by mtime check; or `rm -rf target/`
- Do not add stem-related namespaces to `build.clj` ns-compile (JNI dependency)
- NixOS: patchelf is skipped automatically (`/etc/NIXOS` guard)

### Stem Setup (JVM mode)

```bash
./run.sh stem-setup   # download Kiwi jar + models (one-time)
```

### Performance

| | JVM | Native binary |
|---|---|---|
| Startup | ~1.7s | **0.06s** |
| expand | ~1.7s | **0.06s** |
| build (48,872 entries) | ~2.7s | ~2s |
| Dependency | JDK required | **none (single file)** |

## File Structure

```
dictcli/
├── deps.edn
├── build.clj                  # uberjar build (clj -T:build uber)
├── flake.nix
├── graph.edn                  # triple graph (source of truth, ~3,972 triples)
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
    ├── syntopicon.edn         # Syntopicon 102 Great Ideas
    ├── philosophy.edn
    ├── general.edn
    ├── practical.edn
    ├── meta-sources.edn
    ├── meta-harvest-1.edn
    ├── meta-harvest-2.edn
    └── kengdic-coverage.edn
```

## Tech Stack

- **Clojure** + **GraalVM native-image** — single binary CLI
- **EDN** — triple graph format (human-readable, git-diffable)
- **Kiwi** — Korean morphological analysis (JVM, JNI)
- **NixOS** — reproducible dev environment (`flake.nix`)

## Position in the 3-Layer Model

| Layer | Tool | Role |
|---|---|---|
| 1 | knowledge_search (andenken) | embedding vector search |
| 2 | denotecli + dblock | exact match + graph links |
| **3** | **dictcli expand + stem** | **Korean→English query expansion + morphological stemming** |

## Related Tools

| Tool | Data | Language | Role |
|---|---|---|---|
| bibcli | .bib (8,000+) | Go | bibliography search |
| denotecli | .org filenames (3,000+) | Go | note search |
| lifetract | .db (Samsung Health) | Go | health data |
| gitcli | .git (14,000+ commits) | Go | commit timeline |
| **dictcli** | **graph.edn (~3,972 triples)** | **Clojure** | **vocabulary graph** |

## Philosophy

> Choosing a single translation defines the shape of knowledge.
> Sharing "my words" with an agent is not a technical act — it is a covenant.

> The 500-word guide is not a wall but a gravity field (마당):
> it pulls the agent's vast latent space toward the user's worldview.
> — Gemini (GLG)

## Botlog

- [20260309T194058](https://notes.junghanacs.com/botlog/20260309T194058) — tag normalization and personal vocabulary graph concept (3-axis structure, gravity field, translation problem)
- [20260308T091235](https://notes.junghanacs.com/botlog/20260308T091235) — dblock link format policy
- [20260304T004500](https://notes.junghanacs.com/botlog/20260304T004500) — knowledge graph unconscious agent association map
