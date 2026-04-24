# Magika Algorithm Notes — Phase 1 Plan 1 Gate

> **This document is the Phase 1 Plan 1 hard gate.** No `.java` file under
> `src/main/java/dev/jcputney/magika/{inference,postprocess,io,config}/` may be
> authored before this document is committed (ALG-14). The gate is verified by
> `git log --diff-filter=A --follow` on the first `.java` file under any of
> those subpackages — it must land AFTER the commit introducing this file.
>
> **Authoritative oracle.** Upstream **Python** (`python/src/magika/magika.py`)
> is the ground truth for every parity-relevant fact in this document (D-06).
> Rust is an advisory second source and is only cited in `## Rust-Python
> divergences`. When a downstream plan (2..6) cites "algorithm notes" by
> heading anchor, it is citing this document, not re-reading Python.
>
> **Upstream fetch date.** All upstream line numbers below were verified against
> `github.com/google/magika@main` on 2026-04-24. Research (01-RESEARCH.md)
> listed approximate ranges `:501-540`, `:655-678`, `:792-807`, `:70`; the
> actual ranges on the fetch date are `:578-634`, `:712-792`, `:794-847`, `:60`
> respectively. Footnote: updated from research-date ranges.

---

## Tensor dtype

The `standard_v3_3` ONNX model declares a single input named `bytes` with shape
`[batch, beg_size + end_size] = [batch, 2048]`. The per-element runtime wire
type is `int32` — confirmed by reading `OrtSession.getInputInfo()` at session
creation time in Plan 4 (final confirmation lives in a runtime assertion there,
per PITFALLS.md Pitfall 1 and RESEARCH.md Open Question #3).

**Input tokens are ints in `[0, 256]` where 256 is the padding sentinel.** Java
wire type MUST be `java.nio.IntBuffer`; `byte[]` encodes 256 as `-1` and
silently breaks parity on every file that invokes the padding branch (small
files, sparse files after strip).

Construction path the Java code MUST use (Plan 4):

- `OnnxTensor.createTensor(env, IntBuffer, long[] {1, 2048})` — explicit
  `IntBuffer` in native byte order.
- **Never** reach for `byte[][]` or `OnnxTensor.createTensor(env, byte[][])` —
  the API accepts it, which is why this pitfall is silent rather than loud.
- **Never** `(int)(byte)256`. 256 is not a byte; the model's vocabulary has
  257 symbols (bytes 0..255 plus a padding sentinel at 256).

The Python side materializes this as a `List[List[int]]`; `onnxruntime-python`
coerces to whatever dtype the model declares. Python ints are arbitrary
precision so the padding token "just works." Java has no such escape hatch
(see PITFALLS.md §Pitfall 1).

**Python tensor build:** `magika.py:794-847`, specifically the `X_bytes.append`
loop at `magika.py:803-812` which concatenates `fs.beg[: beg_size]`,
`fs.mid[: mid_size]` (empty for standard_v3_3), and `fs.end[-end_size:]` into a
single 2048-int row. ORT is called as:

```
self._onnx_session.run(["target_label"], {"bytes": batch_features})
```

— input node name is `"bytes"`, output node name is `"target_label"`
(magika.py:837-838). Plan 4's Java engine MUST use those exact string keys.

---

## lstrip byte set

Python `bytes.lstrip()` / `bytes.rstrip()` called with **no argument** strip
the following six bytes, nothing else:

| Byte  | Name                    | Python literal |
|-------|-------------------------|----------------|
| 0x09  | HT  (horizontal tab)    | `\t`           |
| 0x0A  | LF  (line feed)         | `\n`           |
| 0x0B  | VT  (vertical tab)      | `\v`           |
| 0x0C  | FF  (form feed)         | `\f`           |
| 0x0D  | CR  (carriage return)   | `\r`           |
| 0x20  | SP  (space)             | `' '`          |

Source: CPython `Objects/bytesobject.c` — the default whitespace argument for
`bytes.lstrip(None)` is the `Py_STRIP_CHARS` table, which equals
`{' ', '\t', '\n', '\r', '\x0b', '\x0c'}`. This is NOT the Unicode whitespace
set and NOT the C `isspace()` set.

**Explicitly NOT stripped** — these are the common false friends that every
`String`-based Java method strips but Python `bytes.lstrip()` does not:

| Byte  | Name                       | Stripped by                    |
|-------|----------------------------|--------------------------------|
| 0x00  | NUL                        | `String.trim()` (ch <= 0x20)   |
| 0x1F  | US                         | `String.trim()`                |
| 0x85  | NEL  (next line)           | `String.strip()` — Unicode ws  |
| 0xA0  | NBSP (no-break space)      | `String.strip()` — Unicode ws  |
| 0x1C..0x1F | file/group/record/unit separators | `String.trim()`   |
| U+2028, U+2029 | line/paragraph separator | `String.strip()`          |

Any Java port that reaches for `String.strip()`, `String.trim()`, or
`Character.isWhitespace()` for the strip step silently breaks parity on any
file whose first or last bytes land on a mismatched codepoint. See PITFALLS.md
§Pitfall 3 for the full failure-mode explanation.

**Java implementation rule (Plan 2 `ByteStrip`):** operate on `byte[]`
end-to-end. Two helpers, `lstripAscii(byte[])` and `rstripAscii(byte[])`, each
scanning the range `[0, n)` from the appropriate end and bumping a pointer
while `b == 0x09 || b == 0x0A || b == 0x0B || b == 0x0C || b == 0x0D ||
b == 0x20`. Do **not** convert to `String` at any point during feature
extraction.

Unit test the strip helper with a crafted fixture that contains every byte
value `0x00..0xFF` at the start and end, asserting the stripped result matches
CPython's `bytes.lstrip()` output exactly — especially at 0x85 and 0xA0 (see
PITFALLS.md §Pitfall 3 "Warning signs"). 0x00 is a particularly good catch for
`String.trim()` drift.

---

## FP determinism

ONNX Runtime Python and ONNX Runtime Java are **not** bit-exact on identical
model + identical input. The Microsoft ONNX Runtime team characterizes them as
"within ~1e-7 of each other" for well-behaved inputs (see ORT issue #17475).
Sources of non-determinism that can flip a top-1 / top-2 label on ambiguous
inputs:

- **Graph optimization level.** `ORT_ENABLE_BASIC` vs `ORT_ENABLE_ALL` fuses
  ops differently. The upstream Magika Python CLI uses a specific level;
  matching it on the Java side is a prerequisite for reproducibility.
- **Intra-op thread count.** Parallel reductions have non-deterministic order
  of summation when `intraOpNumThreads > 1`. Same input, different run →
  different last-significant bits in logits.
- **Float64 accumulation.** Some Python paths promote to `f64` during reduction
  whereas Java sticks to `f32` throughout.

**The two knobs Plan 4 MUST pin on the Java `SessionOptions`:**

```
SessionOptions opts = new SessionOptions();
opts.setOptimizationLevel(SessionOptions.OptLevel.BASIC_OPT);
opts.setIntraOpNumThreads(1);
```

Both are non-negotiable for Phase 1. `BASIC_OPT` matches upstream's default
optimization level most closely; `intraOpNumThreads=1` makes the inner
reductions deterministic across runs. Revisit ONLY after Phase 1 parity is
stable and a specific performance delta is documented.

**Parity tolerance:** Plan 6's parity harness compares `dl.score` between the
Python oracle and the Java-produced score with `|delta| < 1e-4`. **Strict
equality** is required on the label (POST-04 + TEST-04 — a flipped label is a
parity failure regardless of how small the score delta is). Scores agreeing to
four decimal places and labels disagreeing is a top-1 / top-2 tie-break flip
and is definitively a parity bug; the harness reports it as such, not as a
numerical tolerance miss.

---

## Overwrite-map ordering

### Verbatim pseudocode (`magika.py:578-634`)

Below is the **exact** Python code of
`_get_output_label_from_dl_label_and_score`, lifted from `magika.py` lines
578-634 on the 2026-04-24 upstream fetch. The pseudocode below is copied
verbatim; **no paraphrase**.

```python
def _get_output_label_from_dl_label_and_score(
    self, dl_label: ContentTypeLabel, score: float
) -> Tuple[ContentTypeLabel, OverwriteReason]:
    overwrite_reason = OverwriteReason.NONE

    # Overwrite dl_label if specified in the overwrite_map model config.
    output_label = self._model_config.overwrite_map.get(dl_label, dl_label)
    if output_label != dl_label:
        overwrite_reason = OverwriteReason.OVERWRITE_MAP

    # ... threshold selection (see ## Prediction-mode threshold semantics) ...

    if self._prediction_mode == PredictionMode.BEST_GUESS:
        pass
    elif (
        self._prediction_mode == PredictionMode.HIGH_CONFIDENCE
        and score
        >= self._model_config.thresholds.get(
            dl_label, self._model_config.medium_confidence_threshold
        )
    ):
        pass
    elif (
        self._prediction_mode == PredictionMode.MEDIUM_CONFIDENCE
        and score >= self._model_config.medium_confidence_threshold
    ):
        pass
    else:
        # score too low — fallback to TXT or UNKNOWN
        overwrite_reason = OverwriteReason.LOW_CONFIDENCE
        if self._get_ct_info(output_label).is_text:
            output_label = ContentTypeLabel.TXT
        else:
            output_label = ContentTypeLabel.UNKNOWN
        if dl_label == output_label:
            overwrite_reason = OverwriteReason.NONE

    return output_label, overwrite_reason
```

### 5-step abstract form

```
1. output_label = dl_label; reason = NONE
2. if dl_label in overwrite_map:
       output_label = overwrite_map[dl_label]; reason = OVERWRITE_MAP
3. Determine threshold per PredictionMode (see ## Prediction-mode threshold semantics)
4. if score < threshold:
       reason = LOW_CONFIDENCE
       output_label = TXT if is_text(output_label) else UNKNOWN
5. if output_label == dl_label: reason = NONE   # reassigned to suppress spurious overwrite signal
```

### Ordering is load-bearing

**Step 2 runs BEFORE step 3.** `overwrite_map` is applied BEFORE the threshold
check, not after. Any implementation that reverses this ordering silently
breaks parity on `randombytes` and `randomtxt` fixtures (the only two
overwrite-map entries in standard_v3_3). The disagreement mode is subtle:

- **Correct path:** `dl.label=randombytes`, score=0.9 → overwrite to
  `unknown` (step 2) → threshold check against `randombytes`'s threshold
  (0.5 fallback, since `randombytes` is not in the per-type map) — score 0.9
  > 0.5 → return `unknown`, `OVERWRITE_MAP`.
- **Wrong path (threshold first):** threshold 0.5 satisfied → return
  `randombytes`, `NONE`. Never consult `overwrite_map`. Output label is
  `randombytes` instead of `unknown`.

Also note step 5: the quirk where `overwrite_reason` is reassigned to `NONE`
when `dl_label == output_label` after the LOW_CONFIDENCE fallback. This
matters for text-type predictions where `dl_label` is already `TXT` and the
score fell below threshold — `output_label` is reassigned to `TXT` (same
value), and the reason is reset to `NONE` rather than leaving it at
`LOW_CONFIDENCE`. The `overwrite_reason` is a client-facing signal of "why
does output differ from dl"; if they do not differ, reason must be NONE.

Plan 3's Java `LabelResolver` MUST implement this step literally. Do not split
the logic across classes. A single static method in `postprocess/LabelResolver`
with a comment block naming `magika.py:578-634` and citing this document is
the shape the plan locks.

### Overwrite-map table (standard_v3_3)

The `overwrite_map` carries exactly two entries in `config.min.json`:

| dl.label    | output.label | OverwriteReason  |
|-------------|--------------|------------------|
| randombytes | unknown      | OVERWRITE_MAP    |
| randomtxt   | txt          | OVERWRITE_MAP    |

Parity fixtures MUST exercise both (see 01-VALIDATION.md — Overwrite map
dimension).

### Per-type thresholds table (standard_v3_3)

The `thresholds` map carries exactly twelve per-content-type overrides in
`config.min.json`:

| label      | threshold |
|------------|-----------|
| crt        | 0.9       |
| handlebars | 0.9       |
| ignorefile | 0.95      |
| latex      | 0.95      |
| markdown   | 0.75      |
| ocaml      | 0.9       |
| pascal     | 0.95      |
| r          | 0.9       |
| rst        | 0.9       |
| sql        | 0.9       |
| tsv        | 0.9       |
| zig        | 0.9       |

All other labels fall through to `medium_confidence_threshold = 0.5` via the
`thresholds.get(dl_label, medium_confidence_threshold)` lookup in
`magika.py:600-602`.

### Prediction-mode default

Default is `PredictionMode.HIGH_CONFIDENCE`. Verified at `magika.py:60` —
class constructor default parameter:

```python
prediction_mode: PredictionMode = PredictionMode.HIGH_CONFIDENCE,
```

The `PredictionMode` enum values are `BEST_GUESS`, `MEDIUM_CONFIDENCE`,
`HIGH_CONFIDENCE`; in the Python source they subclass `LowerCaseStrEnum` so
the string values are `"best_guess"`, `"medium_confidence"`, `"high_confidence"`
respectively. Plan 3's Java `PredictionMode` enum MUST match these three names
in this order (ordinal stability matters for any potential future JSON debug).

### OverwriteReason enum

Upstream `python/src/magika/types/overwrite_reason.py` defines exactly three
values:

| Enum value       | Meaning                                                     |
|------------------|-------------------------------------------------------------|
| NONE             | output.label == dl.label (no overwrite applied)             |
| LOW_CONFIDENCE   | score below threshold → TXT/UNKNOWN fallback applied        |
| OVERWRITE_MAP    | dl.label was rewritten via `overwrite_map` lookup           |

Plan 3's Java enum in `postprocess/OverwriteReason` is locked to these three
names, in this order. Do not add values. Do not reorder. If upstream ever
adds a fourth value, Plan 3 updates both the enum and this document.

---

## Small-file branches

Upstream chooses between four size-based branches depending on the file size
`N` (in bytes). **Small-file branches DO NOT invoke `OrtSession.run()`.** This
is what lets `FakeInferenceEngine` (Plan 3, test double) validate postprocess
without ONNX on the classpath.

Python source: the branch cascade lives in `_get_result_or_features_from_seekable`
at `magika.py:712-792`. The relevant thresholds are `min_file_size_for_dl = 8`,
`padding_token = 256`, `block_size = 4096` (from `config.min.json`).

| File size N                                                               | dl.label   | output.label                                               | score       | Rule / Python cite                                               |
|---------------------------------------------------------------------------|------------|------------------------------------------------------------|-------------|------------------------------------------------------------------|
| N == 0                                                                    | UNDEFINED  | EMPTY                                                      | 1.0         | `magika.py:729-736` — no decode, no model                        |
| 0 < N < 8 (min_file_size_for_dl)                                          | UNDEFINED  | TXT if `CharsetDecoder.decode(REPORT)` succeeds, else UNKNOWN | 1.0      | `magika.py:738-741` + `_get_label_from_few_bytes:786-792`        |
| N >= 8 AND `beg[min_file_size_for_dl - 1] == padding_token`               | UNDEFINED  | TXT or UNKNOWN per decode                                  | 1.0         | `magika.py:755-765` — stripped content too short for meaningful DL |
| N >= 8 AND real content survives strip at position 7+                     | (model output) | overwrite-map + threshold resolution                   | from softmax| `magika.py:767-770` — normal path, `OrtSession.run()` invoked    |

Note the score is **1.0** on all three small-file branches (not "low") — this
is what `_get_result_from_few_bytes` sets at `magika.py:774-784`. The score
value itself is mostly a sentinel on these branches; the `output.label`
carries the information.

A worked example for `N` in `[8, block_size)` follows below; it fixes the
end-window reference point that is the non-obvious parity-critical fact for
Plan 3's `ByteWindowExtractor`.

### Worked example (byte-offset walkthrough, N = 3000)

Take `N = 3000` (so `8 <= N < block_size=4096`). This is the range where the
end-window reference point is the non-obvious fact Plan 3's `ByteWindowExtractor`
MUST match.

**Python execution path** (`magika.py:438-469` in `_extract_features_from_seekable`):

```
bytes_num_to_read = min(block_size, seekable.size)  # = min(4096, 3000) = 3000

# beg window
beg_content = seekable.read_at(0, bytes_num_to_read)           # bytes [0, 3000)
beg_content = beg_content.lstrip()                             # strip 0x09/0x0A/0x0B/0x0C/0x0D/0x20 from left
beg_ints = _get_beg_ints_with_padding(beg_content, 1024, 256)  # take first 1024, right-pad with 256

# end window
end_content = seekable.read_at(seekable.size - bytes_num_to_read, bytes_num_to_read)
                                                               # = read_at(3000 - 3000, 3000) = read_at(0, 3000)
                                                               # = bytes [0, 3000) — SAME bytes as beg_content!
end_content = end_content.rstrip()                             # strip from right
end_ints = _get_end_ints_with_padding(end_content, 1024, 256)  # take last 1024, left-pad with 256
```

**This is the load-bearing fact.** When `N < block_size`, the end-window is
computed from the **end of the file** (`seekable.size - bytes_num_to_read`
with `bytes_num_to_read == N`, i.e. offset `0`) — NOT from the end of the
first `block_size`-sized block. For `N = 3000`, `seekable.size -
bytes_num_to_read == 0`, so the end window reads bytes `[0, 3000)` — the
same range as the beg window. The two windows legitimately overlap (same
bytes appear in both `begTokens` and `endTokens` after stripping from
opposite ends).

**Concrete byte offsets for N = 3000:**

| Stage                         | Beg window                            | End window                            |
|-------------------------------|---------------------------------------|---------------------------------------|
| Raw read offsets              | `[0, 3000)`                           | `[0, 3000)` (same bytes as beg)       |
| After strip                   | `lstrip()` trims from the left        | `rstrip()` trims from the right       |
| Sizing (after strip)          | Take **first** 1024, pad right w/ 256 | Take **last** 1024, pad left w/ 256   |
| Result                        | `int[1024]`                           | `int[1024]`                           |
| Concatenated tensor row       | `beg[0..1024)` ++ `end[0..1024)` → `int[2048]` — the model input |

**For `N < 1024`:** both `beg_ints` and `end_ints` end up mostly padding. If
fewer than 1024 bytes survive strip, the helpers right-pad beg (`magika.py:497-499`)
and left-pad end (`magika.py:521-523`) with 256 to reach length 1024.

**For `N >= block_size` (e.g. N = 10000):** `bytes_num_to_read == 4096`. The
end read uses `seekable.size - 4096 == 5904`, so end bytes are `[5904, 10000)`.
The two windows do NOT overlap. Beg reads `[0, 4096)`, end reads `[5904, 10000)`.

**End-slice reference-point rule (for Plan 3 `ByteWindowExtractor`):**

```
endReadOffset = max(0, N - block_size)   # equivalent to seekable.size - bytes_num_to_read
endReadLength = min(block_size, N)
```

An implementation that computes the end slice from the **end of the first
block** (`[block_size - end_size, block_size)` clipped to `N`) is wrong for
every file with `N` in `[8, block_size)` — the end bytes it reads disagree
with upstream.

Cited Python lines:

- `bytes_num_to_read = min(block_size, seekable.size)` — `magika.py:440`
- End read offset `seekable.size - bytes_num_to_read` — `magika.py:462`
- Beg padding (right) — `magika.py:497-499`
- End padding (left) — `magika.py:521-523`

Plan 3's `ByteWindowExtractor` is the load-bearing implementation. See Plan 3
for the Java mapping. An implementation that computes the end slice from the
wrong reference point silently breaks parity on every file in
`[8, block_size)`.

---

## UTF-8 decode

The small-file branch at `magika.py:786-792` uses:

```python
def _get_label_from_few_bytes(self, content: bytes) -> ContentTypeLabel:
    try:
        label = ContentTypeLabel.TXT
        _ = content.decode("utf-8")
    except UnicodeDecodeError:
        label = ContentTypeLabel.UNKNOWN
    return label
```

Python's `bytes.decode("utf-8")` with no `errors` argument uses
`errors="strict"` by default — it raises `UnicodeDecodeError` on any invalid
byte sequence (overlong encodings, lone continuation bytes, truncated
multi-byte sequences, bytes `0x80..0xFF` not part of a valid sequence).

**Java equivalent (Plan 2 / Plan 3 small-file branch):**

```java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPORT)
    .onUnmappableCharacter(CodingErrorAction.REPORT);
try {
    decoder.decode(ByteBuffer.wrap(bytes));
    // success → TXT
} catch (CharacterCodingException e) {
    // failure → UNKNOWN
}
```

`CharacterCodingException` is the Java counterpart to Python's
`UnicodeDecodeError`.

**`new String(bytes, StandardCharsets.UTF_8)` is WRONG** for this branch. It
substitutes `U+FFFD` (the Unicode replacement character) on invalid sequences
and never throws. A Java implementation that uses it will classify every
small-file input as TXT, including inputs the Python oracle classifies as
UNKNOWN. This is the parity-break mechanism PITFALLS.md §Pitfall 4 flags.

The `CharsetDecoder` must be configured with **both** `onMalformedInput(REPORT)`
and `onUnmappableCharacter(REPORT)`. The default action on a fresh
`CharsetDecoder` is `CodingErrorAction.REPORT` for both, but the explicit
configuration makes the contract visible in code review and proof-against a
later developer "relaxing" it.

---

## Stream handling

Contract from 01-CONTEXT.md §D-09 (verbatim; not paraphrased):

**For `identifyStream(InputStream)`, the implementation MUST buffer enough
bytes to cover all three slices (`beg`, `mid`, `end`).** For non-seekable
streams, this means reading to EOF or using a two-pass buffer strategy. An
implementation that only reads `beg_size` bytes will silently break parity on
formats where the signal lives in the tail: ZIP end-of-central-directory, TAR
trailer blocks, PDF xref trailer.

**Concrete buffering requirements for standard_v3_3:**

- `beg_size = 1024`, `mid_size = 0`, `end_size = 1024`, `block_size = 4096`.
- The feature extractor reads two `bytes_num_to_read = min(block_size,
  seekable.size)` windows: one from offset `0` (beg), one from offset
  `seekable.size - bytes_num_to_read` (end). See `## Small-file branches`
  worked example.
- **Minimum bytes to buffer before Plan 5 can call the feature extractor:**
  `block_size = 4096` bytes. This is the upper bound on a single window read.
  For files larger than block_size, the implementation still needs access to
  the LAST block_size bytes of the file — which for a non-seekable stream
  means buffering **up to the full stream content**.
- A non-seekable stream of length `N > block_size`: buffer either the full
  stream (simple; viable for the 50 MB soft-default PITFALLS.md §Pitfall 12
  suggests) OR implement a rolling tail buffer that retains the last
  `block_size` bytes as the stream is consumed.

**Stream ownership contract:**

- The stream is **NOT closed** by the `identifyStream` call — caller owns
  `close()`.
- Post-return stream position is **undefined** (typically at EOF for
  non-seekable streams); Javadoc on `identifyStream` states "do not reuse
  without an external reset."
- Callers needing a reusable source should pass `ByteArrayInputStream` or
  re-open a `FileInputStream` themselves.

**What breaks if the implementation reads only `beg_size` bytes:**

- **ZIP files.** The end-of-central-directory (EOCD) record, the only
  canonical signal for a ZIP file, lives in the last ~22+N bytes of the file.
  A `beg_size`-only read sees `PK\x03\x04` (the local file header signature)
  which the model may classify weakly; the tail is where signal strength
  lives.
- **TAR archives.** TAR uses trailing zero blocks as an end marker. The
  signal for "this is valid TAR" is distributed across the full file; the
  tail is where truncation is detected.
- **PDF.** `%%EOF` + `startxref` live in the last ~100 bytes. PDF trailer is
  the definitive signature.

Plan 3's `ByteWindowExtractor` Javadoc and this document's `## Stream handling`
section are the two places this contract is locked. Plan 5's `Magika.identifyStream`
Javadoc cites this section by heading anchor.

---

## Rust-Python divergences

Per ALG-13, the Rust port of Magika diverges from the Python port in at least
the following areas. **Python is the authoritative oracle per D-06.** Rust is
an advisory second source.

1. **Error handling.**
   - **Python:** raises concrete exception types — `UnicodeDecodeError`
     (small-file decode failure), `OSError` / `FileNotFoundError`
     (`identifyPath` on missing file), `IsADirectoryError` (path is a
     directory). The exception hierarchy is the public contract.
   - **Rust:** returns `Result<T, MagikaError>` with a single error enum.
     Structural and semantic differences: Rust never distinguishes "file not
     found" from "permission denied" at the type level without matching on
     the enum variant.
   - **Impact for Java:** Plan 2's exception hierarchy mirrors the **Python**
     taxonomy (`ModelLoadException`, `InferenceException`,
     `InvalidInputException`) — NOT the Rust single-error shape.

2. **Floating-point rounding.**
   - **Python:** `float` is `f64` (CPython double-precision). Some paths
     accumulate softmax inputs in `f64` before downcasting.
   - **Rust:** uses `f32` throughout, consistent with the ONNX Runtime `float`
     tensor element type. No implicit widening.
   - **Impact for Java:** Java ORT also returns `float` (`f32`). Score
     differences between Python and Java on the same fixture are consistent
     with PITFALLS.md §Pitfall 2's "~1e-7 close" characterization, which is
     exactly why Plan 6's parity harness uses `|delta| < 1e-4` tolerance.
     Rust-side parity would be bit-closer; we do not run Rust as an oracle.

3. **Stream handling (`identifyStream`).**
   - **Python CLI:** reads stdin into a `BytesIO` buffer when piped, then
     treats it as a seekable source. Full-stream materialization.
   - **Rust CLI:** has a different handling for piped stdin — historically
     has read the stream differently and in at least one version did not
     match Python on pipe inputs. Specific divergence is documented in
     upstream's `rust/CHANGELOG.md`; not re-derived here.
   - **Impact for Java:** `identifyStream` contract (D-09, `## Stream handling`
     above) mirrors Python's "read enough to cover all three slices." We do
     NOT match Rust's piped-stdin shape.

4. **CLI flag names and defaults.** (Bonus divergence — not algorithm-critical
   but surfaces when regenerating the Python oracle per D-05.)
   - Python CLI: `--prediction-mode high-confidence` (hyphen).
   - Rust CLI: `--prediction-mode high_confidence` (underscore).
   - Plan 6's oracle regeneration recipe (`fixtures/README.md`) uses the
     Python CLI spelling.

**Authoritative source declaration.** `docs/algorithm-notes.md` (this file)
cites **Python** line numbers exclusively. Anywhere a Rust divergence is
relevant for downstream decisions, it is enumerated here and in the relevant
plan's §"Rust-Python divergences" sub-section — not buried in Rust source
comments. Plan 6's parity harness sidecar JSON (D-04) is generated from the
Python CLI (D-05, D-06).

---

## Model SHA-256

Bundled `model.onnx` SHA-256: `<to be computed at vendor time — populated by
Plan 4 when the .onnx bytes are committed>`.

This placeholder is intentional. Plan 4 is the plan that vendors
`assets/models/standard_v3_3/model.onnx` from upstream into
`src/main/resources/dev/jcputney/magika/models/standard_v3_3/model.onnx`. At
vendor time, Plan 4 computes the SHA-256 of the committed bytes and:

1. Replaces this placeholder line with the actual digest (one commit).
2. Records the same digest in `docs/MODEL_CARD.md` (DOC-06, also a Plan 4
   deliverable), along with the upstream git commit SHA we vendored from and
   the vendoring date.
3. Declares a `public static final String BUNDLED_MODEL_SHA_256` in
   `dev.jcputney.magika.inference.onnx.OnnxModelLoader` that matches this
   value verbatim.

The SHA-256 check at `OnnxModelLoader.load()` time is the tamper / repackaging
canary (MODEL-05): if `SHA-256(bytes) != BUNDLED_MODEL_SHA_256`, the loader
throws `ModelLoadException("Bundled model SHA-256 mismatch: expected=<X>,
observed=<Y>")` and logs at ERROR. Consumer monitoring hooks the error log
without needing to catch the exception (D-11).

Note: `docs/MODEL_CARD.md` is also a placeholder at this document's authoring
time. Plan 4 is the earliest plan that writes it.

---

## content_types_kb.json schema

**Upstream file path — important.** The actual upstream path is
`assets/content_types_kb.min.json`, NOT
`assets/models/standard_v3_3/content_types_kb.json`. Research (01-RESEARCH.md)
referenced the latter shape; the real file lives one directory up from the
model directory, is shared across model versions, and carries the `.min.json`
suffix. `assets/models/standard_v3_3/` contains `config.min.json`,
`metadata.json`, `model.onnx`, and `README.md` — no `content_types_kb*.json`.

Upstream fetch (2026-04-24): file is **44 KiB**, contains **353 content-type
entries** keyed by label name. Top-level JSON shape: `Map<String, Entry>`
where the outer key is the content-type label (e.g. `"3gp"`, `"html"`,
`"python"`) and the value is an object with five fields.

### Field-type map (verbatim from live fetch, 2026-04-24)

| Field        | JSON type               | Java type (for Plan 3 `ContentTypeInfo` record)         | Null observed in 353 entries? |
|--------------|-------------------------|---------------------------------------------------------|-------------------------------|
| `mime_type`  | string (nullable)       | `String`                                                | yes                           |
| `group`      | string (nullable)       | `String`                                                | yes                           |
| `description`| string (nullable)       | `String`                                                | yes                           |
| `extensions` | array of string         | `List<String>`                                          | no (can be empty `[]`)        |
| `is_text`    | boolean                 | `boolean`                                               | no                            |

Sample entry (the `"3gp"` key, upstream-verbatim):

```json
{
  "mime_type": "video/3gpp",
  "group": "video",
  "description": "3GPP multimedia file",
  "extensions": ["3gp"],
  "is_text": false
}
```

### Load-bearing line (grep-verifiable)

- `mime_type` is String

This line is the grep target Plan 3's executor uses to decide the Jackson
record declaration. All 353 upstream entries carry `mime_type` as a **scalar
string** or `null`. **Zero** entries carry `mime_type` as a JSON array. Plan
3's `ContentTypeInfo` record declares:

```java
public record ContentTypeInfo(
    @JsonProperty("mime_type") String mimeType,
    @JsonProperty("group") String group,
    @JsonProperty("description") String description,
    @JsonProperty("extensions") List<String> extensions,
    @JsonProperty("is_text") boolean isText
) { }
```

Note `mimeType`, `group`, and `description` MUST be declared as `String` (not
`Optional<String>`) because Jackson deserializes JSON `null` to Java `null`
for object types. They are nullable per contract.

### Forward-compat caveat

Plan 3 configures the Jackson `ObjectMapper` with
`FAIL_ON_UNKNOWN_PROPERTIES=false` (CFG-05) for forward-compatibility on
additive schema changes (e.g. upstream later adds a `magic_bytes` field).
**BUT that flag does NOT rescue a type mismatch** — if upstream later widens a
scalar field to an array (e.g. `mime_type: ["a/b", "c/d"]`), Jackson throws
`MismatchedInputException` and the flag does not help.

In that case, both this document AND Plan 3's `ContentTypeInfo` record must
be updated — either to `List<String>` or to a custom `@JsonDeserialize` that
normalizes scalar-or-array into `List<String>`. Plan 3's parity test that
reads the real `content_types_kb.min.json` at startup (CFG-05 regression)
catches this at build time, not at first identify call.

---

## config.min.json schema

Upstream file: `assets/models/standard_v3_3/config.min.json`. This is the
**singular** config JSON for the model (a single 2 KiB file). Fetched
2026-04-24.

### Top-level field-type map

| Field                           | JSON type      | Standard_v3_3 value                | Role                                                              |
|---------------------------------|----------------|------------------------------------|-------------------------------------------------------------------|
| `beg_size`                      | int            | `1024`                             | tokens kept from beg window after strip                           |
| `mid_size`                      | int            | `0`                                | tokens from middle (always 0 for standard_v3_3)                   |
| `end_size`                      | int            | `1024`                             | tokens kept from end window after strip                           |
| `use_inputs_at_offsets`         | boolean        | `false`                            | always false in Phase 1 (feature extractor asserts it)            |
| `medium_confidence_threshold`   | number         | `0.5`                              | fallback threshold for labels not in `thresholds`                 |
| `min_file_size_for_dl`          | int            | `8`                                | below this size skip the model                                    |
| `padding_token`                 | int            | `256`                              | sentinel; distinct from any byte value                            |
| `block_size`                    | int            | `4096`                             | byte window BEFORE strip                                          |
| `target_labels_space`           | List<String>   | 200 labels (see below)             | model's label vocabulary (softmax output width)                   |
| `thresholds`                    | Map<String,number> | 12 entries                     | per-type threshold overrides                                      |
| `overwrite_map`                 | Map<String,String> | 2 entries                      | applied BEFORE threshold (see `## Overwrite-map ordering`)        |
| `protection`                    | String         | `"none"`                           | reserved; `"none"` in Phase 1                                     |
| `aes_key_hex`                   | String         | `""` (empty)                       | reserved; empty in Phase 1                                        |
| `version_major`                 | int            | `3`                                | config schema major version                                       |

`target_labels_space` carries 200 strings — sample prefix:
`["3gp","ace","ai","aidl","apk","applebplist","appleplist","asm","asp","autohotkey", ...]`.
Plan 3's `MagikaConfig` record declares this as `List<String>` and does not
otherwise interpret it; it is the softmax output label vocabulary.

**Note on `protection` + `aes_key_hex`.** These are upstream-reserved fields
for an encrypted-model feature that has not shipped. Phase 1 treats them as
opaque passthrough; Plan 3's `MagikaConfig` record declares them as `String`
and `String` respectively, and Plan 4's `OnnxModelLoader` asserts
`protection.equals("none")` at load time. If upstream ever ships a non-"none"
value, Plan 4 throws `ModelLoadException("Encrypted model not supported;
expected protection='none', got '" + protection + "'")`.

Plan 3 configures the Jackson `ObjectMapper` with:

```java
ObjectMapper MAPPER = JsonMapper.builder()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)   // CFG-05 forward-compat
    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)   // catches corruption
    .build();
```

See PITFALLS.md §Pitfall 9 for why both flags are load-bearing.

---

## Prediction-mode threshold semantics

The exact threshold formula for each `PredictionMode`, with Python citations.
Cited from `magika.py:593-615` — the `if/elif/elif/else` cascade inside
`_get_output_label_from_dl_label_and_score`.

**Summary.** `BEST_GUESS` uses threshold 0.0 (no check). `MEDIUM_CONFIDENCE`
uses the global `medium_confidence_threshold` scalar (0.5 for standard_v3_3)
and ignores the per-type `thresholds` map. `HIGH_CONFIDENCE` uses
`thresholds.get(dl_label, medium_confidence_threshold)` — per-type override
where present, 0.5 otherwise. Cited line: `magika.py:610` for the
MEDIUM_CONFIDENCE threshold formula.

### BEST_GUESS

```
effective_threshold = 0.0
```

No threshold check. The (potentially overwrite-map-adjusted) `output_label`
is returned regardless of `score`. Python: `magika.py:593-596` — a `pass`
inside the `if BEST_GUESS` branch.

### MEDIUM_CONFIDENCE

```
effective_threshold = medium_confidence_threshold    # the scalar from config.min.json
                                                     # standard_v3_3 value: 0.5
```

Cited Python line: **`magika.py:610`** — the condition is
`score >= self._model_config.medium_confidence_threshold`. This is NOT
`min(thresholds.get(dl_label), medium_confidence_threshold)`,
NOT `max(thresholds.get(dl_label), medium_confidence_threshold)`, and NOT a
per-label lookup — just the global `medium_confidence_threshold` scalar.
Unambiguous; the per-type `thresholds` map is **ignored** in MEDIUM_CONFIDENCE
mode.

Plan 3's `LabelResolver` implements this literally: in MEDIUM_CONFIDENCE
mode, use `config.mediumConfidenceThreshold()` — ignore the per-type map.

### HIGH_CONFIDENCE (default — `magika.py:60`)

```
effective_threshold = thresholds.get(dl_label, medium_confidence_threshold)
                    # per-type from config.min.json `thresholds` map,
                    # fallback to medium_confidence_threshold (0.5) for
                    # labels not listed
```

Cited Python lines: **`magika.py:598-602`** — the condition is
`score >= self._model_config.thresholds.get(dl_label,
self._model_config.medium_confidence_threshold)`.

For the 12 labels in the per-type thresholds table (see `## Overwrite-map
ordering`), the effective threshold is the per-type override (e.g. `markdown`
→ 0.75, `ignorefile` → 0.95). For the other 188 labels in
`target_labels_space`, the effective threshold is 0.5.

### Summary

| Mode               | Effective threshold formula                                                  | Python line(s)       |
|--------------------|------------------------------------------------------------------------------|----------------------|
| BEST_GUESS         | 0.0 (skip)                                                                   | `magika.py:593-596`  |
| MEDIUM_CONFIDENCE  | `medium_confidence_threshold` (scalar, 0.5 for standard_v3_3)                | `magika.py:610`      |
| HIGH_CONFIDENCE    | `thresholds.get(dl_label, medium_confidence_threshold)` — per-type or 0.5    | `magika.py:598-602`  |

**Parity-fixture implication for Plan 6.** The MEDIUM_CONFIDENCE parity
fixture (01-VALIDATION.md §Prediction mode dimension) MUST be regenerated
with `magika --prediction-mode medium_confidence <fixture>` — the default
mode is HIGH_CONFIDENCE and a fixture regenerated with the default will only
exercise the HIGH_CONFIDENCE branch. Without an explicitly-regenerated
MEDIUM_CONFIDENCE fixture, no test catches a `LabelResolver` that silently
guesses a wrong MEDIUM_CONFIDENCE formula.

Plan 3's `LabelResolver` is the single implementation of this switch. Do not
split the mode selector across classes. A single static method with three
branches citing each Python line range is the shape Plan 3 locks.

---

## Cross-references

- **Downstream plans (Plans 2..6) cite this document by heading anchor.**
  Example: Plan 3 cites `## Overwrite-map ordering` rather than re-reading
  `magika.py:578-634`. If a plan needs a fact this document does not carry,
  file it as a deviation (Rule 4 — architectural question) before inventing
  one.
- **Parity pitfalls** (01-CONTEXT.md §Parity pitfalls, PITFALLS.md §Critical):
  every critical pitfall has a mitigation in exactly one section of this
  document. Cross-reference:
  - Pitfall 1 (tensor dtype) → `## Tensor dtype`
  - Pitfall 2 (FP determinism) → `## FP determinism`
  - Pitfall 3 (strip semantics) → `## lstrip byte set`
  - Pitfall 4 (small-file / UTF-8) → `## Small-file branches` + `## UTF-8 decode`
  - Pitfall 5 (overwrite-map ordering) → `## Overwrite-map ordering`
  - Pitfall 8 (lifecycle) → not here (API concern, Plan 5 Javadoc)
  - Pitfall 9 (Jackson records) → `## content_types_kb.json schema` +
    `## config.min.json schema`
  - Pitfall 12 (stream re-read) → `## Stream handling`
- **Locked decisions** (01-CONTEXT.md §decisions):
  - D-06 (Python as oracle) → cited in header + `## Rust-Python divergences`
  - D-09 (stream buffer contract) → `## Stream handling` (verbatim)
  - D-11 (SHA log) → `## Model SHA-256`

---

## Footnotes

1. **Line-number drift from research.** 01-RESEARCH.md §Plan 1 cited
   approximate ranges `:501-540` (overwrite), `:655-678` (small-file),
   `:792-807` (tensor), `:70` (prediction-mode default). The 2026-04-24
   upstream fetch shows actual ranges `:578-634`, `:712-792`, `:794-847`,
   `:60` respectively. Updated from research-date ranges. No semantic drift
   observed — the code at the new line numbers is the same code the research
   described; upstream has added/removed prior lines in the file.

2. **`content_types_kb` path.** Research and the plan file refer to
   `content_types_kb.json`. The upstream file is
   `assets/content_types_kb.min.json` (with `.min.json` suffix), and it is
   **not** inside the standard_v3_3 model directory — it is one level up and
   shared across model versions. The standard_v3_3 model directory
   (`assets/models/standard_v3_3/`) contains `README.md`, `config.min.json`,
   `metadata.json`, and `model.onnx` — no `content_types_kb*.json`. Plan 4's
   vendoring step consumes the `.min.json` file and places it alongside
   `model.onnx` + `config.min.json` at
   `src/main/resources/dev/jcputney/magika/models/standard_v3_3/`.
