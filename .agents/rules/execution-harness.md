# Task Execution Harness (Mandatory for Every Coding Request)

1. **Gate 1 — Grounding & Assumptions**: Read `.agents/CODEBASE_MAP.md`, `.agents/memory/lessons.md`, and `referenceRepo` conventions. Surface assumptions upfront; track > 3-file work in `.agents/memory/active-plan.md`.
2. **Gate 2 — Surgical File Split**: Touch only required files/lines (`karpathy-skills`). Keep 1 primary responsibility per file (`> 300 LOC` = WARN, `> 450 LOC` = FAIL).
3. **Gate 3 — Implementation & 3-Strike Debugging**:
   - **Feature**: `Domain` (entities, narrow ports, `DomainError`) -> `Data` (DTO/Entity, Mappers, RepoImpl) -> `State Holder` (Sealed `UiState`, `Intent`) -> `Stateless UI` -> `DI/Nav`.
   - **Bugfix**: Trace root cause; test 1 hypothesis at a time; **STOP after 3 failed attempts** (`obra/superpowers`) to reassess architecture.
   - **No Leftovers**: Ban `// TODO`, fake production returns, unused imports, or swallowed `CancellationException`/`CancelledError`.
4. **Gate 4 — Edge-Case Unit Tests**: Cover happy path, empty state, offline/error mapping, and invalid inputs using fast in-memory **Fakes**.
5. **Gate 5 — Paranoid Check & Self-Healing**: Audit diff for race conditions, N+1 queries, and unbounded lists; run `./gradlew :feature:<name>:testDebugUnitTest --tests '*<ClassName>*'` and `./gradlew ktlintCheck detekt lintDebug` until green.
6. **Gate 6 — Lessons & Terse Output**: Append new project gotchas to `.agents/memory/lessons.md`; deliver concise high-signal summary (`caveman`).
