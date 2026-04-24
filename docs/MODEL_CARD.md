# Model Card — magika-java

## Bundled model: standard_v3_3

| Field | Value |
|-------|-------|
| Upstream repo | https://github.com/google/magika |
| Upstream git SHA | `363a44183a6f300d5d7143d94a19e6a841671650` |
| Upstream source path | `assets/models/standard_v3_3/model.onnx` |
| Upstream release | `main` (no release tag at this SHA) |
| Vendoring date | `2026-04-24` |
| Committed file size | `3163737` bytes (~3.0 MB) |
| Committed SHA-256 | `fe2d2eb49c5f88a9e0a6c048e15d6ffdf86235519c2afc535044de433169ec8c` |
| License | Apache License 2.0 (upstream `LICENSE` vendored alongside as `dev/jcputney/magika/models/standard_v3_3/LICENSE`) |

## Bundled companions

Two JSON configuration files are vendored alongside `model.onnx`:

| Resource | Upstream path | Upstream fetched size | Notes |
|----------|---------------|-----------------------|-------|
| `config.min.json` | `assets/models/standard_v3_3/config.min.json` | 2141 bytes | Byte-for-byte copy. 214 labels in `target_labels_space` at this SHA. |
| `content_types_kb.json` | `assets/content_types_kb.min.json` | 44768 bytes | **Shape-transformed at vendor time** (see below). |

### `content_types_kb.json` shape transformation

Upstream ships `assets/content_types_kb.min.json` as a `Map<String, Entry>`
keyed by label (e.g. `"3gp": { "mime_type": "video/3gpp", ... }`).
Plan 3's Jackson record `ContentTypeInfo` + `ContentTypeRegistry.fromList(...)`
seam parses an **array** of entries with `label` as an explicit field:
`[{ "label": "3gp", "mime_type": "video/3gpp", ... }, ...]`.

At vendor time (`curl` + one Python pass), the upstream map is flattened
into the array shape. No field values are modified — only the outer-shape
mapping is re-expressed as `{label, group, mime_type, description, extensions, is_text}`
tuples. The committed file contains all 353 upstream content-type entries.

## Verification

On `Magika` construction, `OnnxModelLoader.loadAndVerify()` computes the
SHA-256 of the bundled bytes and compares against the constant
`EXPECTED_SHA256` in `OnnxModelLoader.java`. Mismatch:

- Logs ERROR on the `dev.jcputney.magika.Magika` logger with expected +
  observed SHAs (D-11).
- Throws `ModelLoadException("model SHA-256 mismatch: expected <x> got <y>")`.

The SHA in this document, in `OnnxModelLoader.EXPECTED_SHA256`, and in
`docs/algorithm-notes.md` §Model SHA-256 **must match byte-for-byte**.
A mismatch on any axis means the bundled model will be rejected at load
time.

## Refresh procedure (manual)

Refreshing the bundled model is a manual copy-from-upstream step. No
script — per the project rule "no helper scripts / CLI utilities." Steps:

1. Pick a new upstream git SHA.
2. `curl -L -o src/main/resources/dev/jcputney/magika/models/standard_v3_3/model.onnx https://raw.githubusercontent.com/google/magika/<SHA>/assets/models/standard_v3_3/model.onnx`
3. Same for `config.min.json` (same upstream dir). For `content_types_kb.json`,
   download `assets/content_types_kb.min.json` (note the `.min.json` suffix and
   repo-root location — NOT inside `standard_v3_3/`), then re-run the
   map-to-array transform.
4. Re-vendor upstream `LICENSE` (same `curl` against upstream `LICENSE`).
5. Compute new SHA-256 with `shasum -a 256 src/main/resources/.../model.onnx`.
6. Update `EXPECTED_SHA256` constant in `OnnxModelLoader.java` to match.
7. Update this MODEL_CARD.md (upstream git SHA, vendoring date, file size, SHA-256).
8. Update `docs/algorithm-notes.md` §Model SHA-256 to match.
9. Run `mvn -B verify` — all three CI legs must pass before the new model lands.

## Size-threshold decision record (D-03)

**Trigger:** If `model.onnx` exceeds **10 MB** OR a second model version
ships alongside `standard_v3_3`, the model resources extract to a
separate `magika-java-model` artifact per MOD-03 (Phase 4). Do not
accumulate multiple multi-MB binaries in this module's git history.

**Current state (Phase 1):** `standard_v3_3/model.onnx` is
**3,163,737 bytes (~3.0 MB)** — under the 10 MB threshold; bundling
in-tree is acceptable.

## D-02 provenance decision record

This document is the authoritative provenance record for the bundled
model. Every bundled asset is sourced from the single pinned upstream
git SHA above; any future refresh must re-verify the six fields in
the "Bundled model" table and re-record the new SHA-256.
