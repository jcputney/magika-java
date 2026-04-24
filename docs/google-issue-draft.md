# Upstream issue draft — google/magika

**Status:** draft. To be filed post-Phase-1 (Phase 3 readiness).

---

## Subject

Community Java binding — questions about model-versioning contract and bindings linkage

## Body

Hello Magika team,

We are building `magika-java`, an independent Java binding for Magika's
ONNX file-type detection (Apache 2.0; ships the `standard_v3_3` model).
Released under `dev.jcputney:magika-java` (DNS-verified Central Portal
namespace). This is **not** asking for endorsement — just three questions
to make sure the binding is long-lived:

### 1. Model-config schema stability

Is the `config.min.json` / `content_types_kb.json` schema stable across
model revisions? Specifically: when a new `standard_vN_M` ships, can we
expect the same top-level keys (`beg_size`, `mid_size`, `end_size`,
`block_size`, `padding_token`, `min_file_size_for_dl`,
`medium_confidence_threshold`, `thresholds`, `overwrite_map`,
`target_labels_space`, `version_major`), with purely additive changes? Or
does each model revision reserve the right to restructure or rename
fields?

We've configured Jackson with `FAIL_ON_UNKNOWN_PROPERTIES=false` to stay
forward-compatible, but we'd like to know if there's a point at which
strict validation becomes safe.

### 2. Community-binding linkage

Would Google consider linking community-maintained bindings (this library,
plus the existing Rust and Go bindings people have filed) from the main
`google/magika` README? Not asking for endorsement — asking for a pointer
so users can find binding-compatible language options.

### 3. GroupId / naming conventions

Is there a preference for groupId naming across community bindings?
(E.g., `dev.<author>` vs `io.github.<author>` vs
`com.google.magika.bindings.*`.) We've chosen `dev.jcputney` (DNS-verified)
but are happy to rename if there's an opinion from the team.

Thanks for the excellent model and for open-sourcing it.

---

## Filing notes

- File via GitHub issue on `google/magika` with the `question` label.
- Phase 3 milestone: respond to any reply; update README with response
  link.
- Not blocking on any reply for Phase 1 or 2 completion.
