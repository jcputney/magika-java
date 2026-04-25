# Parity fixtures

35 upstream-anchored fixtures for the `UpstreamParityIT` parity harness
(30 v0.1 + 5 added in Plan 02-02 for TEST-11/12/13).
Every fixture has clear Apache 2.0 or public-domain provenance; every
fixture has a sibling `<name>.<ext>.expected.json` file generated from
the pinned upstream Magika Python package.

## Oracle pin (D-07)

See `ORACLE_VERSION` (machine-readable KEY=VALUE lines):

```
ORACLE_MAGIKA_VERSION=1.0.2
ORACLE_MAGIKA_GIT_SHA=363a44183a6f300d5d7143d94a19e6a841671650
```

The parity harness reads `ORACLE_VERSION` via `FixtureLoader.readOracleVersion`
and logs both keys on every test-run start so CI failure messages
carry the oracle version context (D-07).

## Regeneration recipe (D-05)

Refreshing sidecars is a manual, documented procedure — **not** a
checked-in script (project rule: no helper scripts / CLI utilities).

1. Install the pinned upstream Python package in a throwaway environment:
   ```
   uv tool run --from "magika @ git+https://github.com/google/magika.git@<ORACLE_MAGIKA_GIT_SHA>#subdirectory=python" python3
   ```
   (Or equivalent `pipx install` / `pip install` invocation pinning the
   same git SHA so the bundled `standard_v3_3` model matches what
   `magika-java` ships.)

2. For each fixture, call the Python API directly so `overwrite_reason`
   is available (the CLI `--json` output omits it):

   ```python
   from magika import Magika
   m = Magika()
   r = m.identify_path(fixture_path)
   pred = r.prediction   # .dl / .output / .score / .overwrite_reason
   ```

3. Write each sidecar with the D-04 schema (sorted keys, 2-space indent):

   ```json
   {
     "dl": { "label": "<label>", "score": <float>, "overwriteReason": "NONE" },
     "output": { "label": "<label>", "score": <float>, "overwriteReason": "<NONE|LOW_CONFIDENCE|OVERWRITE_MAP>" },
     "score": <float>,
     "upstream_magika_version": "1.0.2",
     "upstream_magika_git_sha": "363a44183a6f300d5d7143d94a19e6a841671650"
   }
   ```

   Notes:
   - `dl.overwriteReason` is always `"NONE"` by construction — the
     reason describes why `output` differs from `dl`, not `dl` itself.
   - `dl.score` and `output.score` both equal the single top-level
     `score` reported by upstream (the prediction carries one score,
     not per-column scores).
   - Upstream `overwrite_reason` values are lowercase (`'none'`,
     `'low_confidence'`, `'overwrite_map'`); the sidecar writes them
     uppercase to match our Java `OverwriteReason` enum names.

4. Update `ORACLE_VERSION` if the pin changed.
5. Run `mvn -B -ntp verify` on all three CI legs before committing.

### Non-default prediction modes (TEST-12 / TEST-13)

For fixtures that exercise non-default prediction modes, instantiate `Magika`
with the `prediction_mode` kwarg before identifying the fixture bytes. The
pinned upstream `magika==1.0.2` accepts the `PredictionMode` enum directly
(verified: `inspect.signature(Magika.__init__)` exposes `prediction_mode`):

```python
from magika import Magika, PredictionMode

# MEDIUM_CONFIDENCE (TEST-12)
m = Magika(prediction_mode=PredictionMode.MEDIUM_CONFIDENCE)
r = m.identify_path(fixture_path)

# BEST_GUESS (TEST-13)
m = Magika(prediction_mode=PredictionMode.BEST_GUESS)
r = m.identify_path(fixture_path)
```

The result shape is identical to default-mode invocation
(`r.prediction.dl`, `r.prediction.output`, `r.prediction.overwrite_reason`,
`r.prediction.score`). The sidecar carries the mode-specific result; the
Java side selects the matching `Magika` singleton (`MAGIKA_MEDIUM` or
`MAGIKA_BEST` in `UpstreamParityIT`) by filename prefix
(`medium-confidence-*` and `best-guess-*` respectively).

For mode-divergent fixtures, sanity-check that the SAME bytes produce a
DIFFERENT `output.label` (or `output.overwriteReason`) when run under
HIGH mode — otherwise the fixture is not actually exercising the mode it
claims (Pitfall 5 in `02-RESEARCH.md`). Example output for
`medium-confidence-1.bin`:

```
MEDIUM: dl=ignorefile output=ignorefile reason=none score=0.7463
HIGH:   dl=ignorefile output=txt        reason=low_confidence score=0.7463  <-- diverges
```

## Per-fixture provenance

| Fixture | Source | License | Dimensions exercised |
|---------|--------|---------|---------------------|
| `code/sample.py` | Handwritten | Public domain (author Jonathan Putney) | format=code, size>block_size path |
| `code/sample.java` | Handwritten | Public domain | format=code |
| `code/sample.js` | Handwritten | Public domain | format=code |
| `code/sample.rs` | Handwritten | Public domain | format=code |
| `code/sample.go` | Handwritten | Public domain | format=code |
| `text/sample.md` | Handwritten | Public domain | format=text, per-type threshold=0.75 (markdown) |
| `text/sample.json` | Handwritten | Public domain | format=structured-text, valid UTF-8 |
| `text/sample.yaml` | Handwritten | Public domain | format=structured-text |
| `text/sample.csv` | Handwritten | Public domain | format=structured-text |
| `images/sample.png` | Handwritten (1x1 transparent PNG spec) | Public domain | format=image |
| `images/sample.jpg` | Handwritten (1x1 JPEG baseline) | Public domain | format=image |
| `images/sample.gif` | Handwritten (GIF89a 1x1) | Public domain | format=image |
| `archives/sample.zip` | Python `zipfile.ZipFile` one-entry | Public domain | format=archive, tail-signal (EOCD at end) — **D-09 regression: routed through `identifyStream`** |
| `archives/sample.tar` | Python `tarfile` two-entry | Public domain | format=archive, tail-signal (trailer blocks) |
| `archives/sample.gz` | Python `gzip` | Public domain | format=archive |
| `documents/sample.pdf` | Handwritten PDF 1.4 minimal | Public domain | format=document, tail-signal (xref at end) — **D-09 regression: routed through `identifyStream`** |
| `structured/sample.xml` | Handwritten | Public domain | format=structured-data |
| `structured/sample.toml` | Handwritten | Public domain | format=structured-data |
| `edge/empty.bin` | 0 bytes | Public domain | size=0 (EMPTY sentinel) |
| `edge/one-byte.bin` | 0x41 | Public domain | size=1, UTF-8 valid → TXT |
| `edge/one-ff.bin` | 0xFF | Public domain | size=1, UTF-8 invalid → UNKNOWN |
| `edge/below-threshold.bin` | "test" (4 bytes) | Public domain | size<min_file_size_for_dl, UTF-8 valid → TXT |
| `edge/at-threshold.bin` | "testtest" (8 bytes) | Public domain | size==min_file_size_for_dl, LOW_CONFIDENCE path (model runs, score < threshold) |
| `edge/one-space.txt` | 0x20 | Public domain | lstrip (single space → strips to empty) |
| `edge/nbsp-prefix.txt` | NBSP (0xC2 0xA0) + "hello world\n" | Public domain | lstrip (NBSP must NOT strip), LOW_CONFIDENCE path |
| `edge/random-bytes.bin` | 64 bytes from deterministic Python `Random(20260424)` | Public domain | overwrite-map (randombytes → unknown, OVERWRITE_MAP) |
| `edge/stream-empty.bin` | 0 bytes | Public domain | **CR-01 regression** — `identifyStream(empty)` must hit small-file branch (EMPTY) |
| `edge/stream-one-text-byte.txt` | 0x41 ('A') | Public domain | **CR-01 regression** — `identifyStream(N=1, valid UTF-8)` must hit small-file branch (TXT) |
| `edge/stream-seven-bytes.bin` | 7 invalid-UTF-8 bytes (`FF C0 80 81 FE C1 FD`) | Public domain | **CR-01 regression** — `identifyStream(N=7, invalid UTF-8)` must hit small-file branch (UNKNOWN) |
| `edge/path-leading-whitespace.txt` | 1024 spaces + `AB` (1026 bytes) | Public domain | **CR-02 regression** — N&gt;=min_file_size_for_dl but stripped beg has &lt;8 real tokens; algorithm-notes §"Small-file branches" row 3 — model NOT invoked, output=TXT |
| `edge/randomtxt.bin` | 256 alnum bytes from deterministic Python `Random(20260425)` over `string.ascii_letters + string.digits`; landed on `dl.label="randomtxt"` under pinned oracle. Do NOT regenerate — bytes pinned. | Public domain | **TEST-11 (Plan 02-02)**: overwrite-map (`randomtxt → txt`, OVERWRITE_MAP) under HIGH mode — makes the OVERWRITE_MAP signal load-bearing in `assertParity` |
| `edge/medium-confidence-1.bin` | 18 bytes `# comment\nfoo.bak\n` (handwritten `.gitignore`-shaped fragment); pinned oracle scores `dl=ignorefile @ 0.7463` | Public domain | **TEST-12 #1 (Plan 02-02)**: MEDIUM_CONFIDENCE keeps `output=ignorefile NONE`; under HIGH the same bytes fall back to `output=txt LOW_CONFIDENCE` (per-type ignorefile threshold = 0.95) |
| `edge/medium-confidence-2.bin` | 24 bytes `let f x = x + 1;;\nf 5;;\n` (handwritten OCaml fragment); pinned oracle scores `dl=ocaml @ 0.6267` | Public domain | **TEST-12 #2 (Plan 02-02)**: MEDIUM_CONFIDENCE keeps `output=ocaml NONE`; under HIGH the same bytes fall back to `output=txt LOW_CONFIDENCE` (per-type ocaml threshold = 0.9) |
| `edge/best-guess-1.bin` | 14 bytes `void main(){}\n` (Dart-shaped fragment, intentionally too short for confident classification); pinned oracle scores `dl=dart @ 0.1608` | Public domain | **TEST-13 #1 (Plan 02-02)**: BEST_GUESS keeps `output=dart NONE` (threshold 0.0); under HIGH the same bytes fall back to `output=txt LOW_CONFIDENCE` |
| `edge/best-guess-2.bin` | 12 bytes `192.168.0.1\n` (single IP-address line); pinned oracle scores `dl=csv @ 0.1888` | Public domain | **TEST-13 #2 (Plan 02-02)**: BEST_GUESS keeps `output=csv NONE` (threshold 0.0); under HIGH the same bytes fall back to `output=txt LOW_CONFIDENCE` |

## Dimension coverage summary

Maps to `01-VALIDATION.md` §"Validation Dimensions":

- **Format family:** code (5), document (1), image (3), archive (3), text (4), structured (2), edge (8) → 7 families, all populated.
- **Size class:** 0 B (empty), 1 B (one-byte, one-ff, one-space), 4 B (below-threshold), 8 B (at-threshold), 14 B (nbsp-prefix), and files above block_size (code/archive/structured/image fixtures). Full size-class coverage.
- **Tail-signal:** zip (EOCD), tar (trailer), pdf (xref) — all present.
- **UTF-8 boundary:** valid (most text) vs invalid (`edge/one-ff.bin`).
- **lstrip byte set:** `edge/one-space.txt` (strips), `edge/nbsp-prefix.txt` (NBSP does NOT strip — Pitfall 3 regression).
- **Overwrite map:** `edge/random-bytes.bin` (`randombytes → unknown`, OVERWRITE_MAP) and `edge/randomtxt.bin` (`randomtxt → txt`, OVERWRITE_MAP — Plan 02-02 TEST-11). Both overwrite-map entries from `config.min.json` now have a parity fixture.
- **Threshold branches:** `edge/at-threshold.bin` and `edge/nbsp-prefix.txt` land LOW_CONFIDENCE (score < per-type threshold, output falls back to `txt`).
- **Prediction mode:** Plan 02-02 (TEST-12 / TEST-13) added 4 mode-divergent fixtures (`edge/medium-confidence-{1,2}.bin` under MEDIUM_CONFIDENCE; `edge/best-guess-{1,2}.bin` under BEST_GUESS). The remaining 30 v0.1 fixtures plus `edge/randomtxt.bin` exercise default HIGH_CONFIDENCE. `UpstreamParityIT` exposes three Magika singletons (`MAGIKA_HIGH`, `MAGIKA_MEDIUM`, `MAGIKA_BEST`) and routes fixtures by filename prefix.

## Flags / caveats

- `edge/random-bytes.bin` — 64 frozen bytes from deterministic seed 20260424.
  Do **NOT** regenerate with a different seed; the sidecar is pinned to
  these exact bytes. If the seed ever changes, the sidecar score will
  drift and parity will break on a "same fixture, different bytes" bug.

- `edge/randomtxt.bin` — 256 frozen alnum bytes from deterministic seed
  20260425 over `string.ascii_letters + string.digits` (Plan 02-02 TEST-11).
  Do **NOT** regenerate with a different seed or a different character set:
  the upstream model only classifies a narrow band of high-entropy text
  shapes as `randomtxt` (full `string.printable` lands as `randombytes`;
  alnum lands as `randomtxt`). The sidecar is pinned to these exact bytes;
  reseeding will drift the score and likely flip `dl.label` to a different
  class (verified during fixture authoring — see Plan 02-02 SUMMARY).

- `archives/sample.zip` and `documents/sample.pdf` — the
  `UpstreamParityIT.identifyStreamExercised_zip_tail_signal` and
  `identifyStreamExercised_pdf_tail_signal` tests route these through
  `identifyStream` to verify the D-09 buffer-all-slices contract.

- If upstream labels drift between `ORACLE_MAGIKA_VERSION` bumps (e.g.
  `java` → `sourcejava`), regenerate all sidecars with the new pin,
  update this README, update `ORACLE_VERSION`, and re-run the full
  matrix.
