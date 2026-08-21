# Acknowledgements

This project is a port of **[HKUDS/LightRAG](https://github.com/HKUDS/LightRAG)**.

## Licence of the original

MIT License, © 2025 LightRAG Team. Read from `LICENSE` at the root of the repository at
commit `79c7e3619bd057b30b40fb4e88668d38faadcf86`, not from a badge. A copy is kept
alongside this file as `LICENSE-lightrag`.

## What was copied

**No source was copied.** Every Java file here was written against
`lightrag-port/specs/SPEC-001-lightrag.md`, which describes the behaviour in prose and
numbered rules.

Two data files were produced by running the original and are checked in under
`src/test/resources/bench/`:

- `workloads.json` — inputs written for this port, not taken from the original.
- `answers-python.json` — what the original returned for those inputs, recorded by
  `lightrag-port/bench/run_source.py`. It is the original's output, not its source.

No prompts, schemas, fixtures or test corpora were taken.

## What is derived

**The behaviour is derived, and deliberately so.** This port exists to give the same
answers as `HKUDS/LightRAG` for the slice it covers — which branch a query mode runs,
how two result lists are interleaved, where a token budget cuts a list, which passage
belongs to which entity, and how citations are numbered. Those rules were established by
running the original and are reproduced here on purpose. Where this port decided
something the original leaves open, the README says which and why.

MIT permits this without conditions beyond keeping the notice, which `LICENSE-lightrag`
does.

## Also used

- [Akka](https://akka.io) — the runtime and SDK this is built on.
- [jtokkit](https://github.com/knuddelsgmbh/jtokkit) (MIT) — the byte-pair token
  vocabulary, so that a token budget is cut in the same place as the original cuts it.
