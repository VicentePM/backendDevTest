# OpenSpec — backenddevtest

Spec-Driven Development artifacts for the Zara Pre-owned similar-products
technical test.

## Layout

- `config.yaml` — project context, stack, testing capabilities, phase rules.
- `specs/` — canonical capability specs (populated on archive).
- `changes/` — active proposals (`{change-id}/proposal.md`, `specs/`, `design.md`, `tasks.md`).
- `changes/archive/` — completed changes.

## Persistence

Hybrid mode: artifacts live here as files AND are mirrored to Engram
(topic keys under `sdd-init/backenddevtest`, `sdd/backenddevtest/*`, and
per-change topic keys).
