# Spex DIG-Inspired Description Method

## Source interpretation

The referenced [Spex repository](https://github.com/sublang-ai/spex) treats `specs/` as the source of truth and starts navigation at a spec map.
Its current scaffold separates decision records, intent records, and behavior packages.
A package declares its intent, externally observable behavior, optional internal behavior, and verification.
Cross-package behavior is represented by another ordinary package with explicit citations rather than by an untraceable narrative.

The historical `DIG: Daily Digest` example is a useful description pattern rather than a universal acronym.
It describes one emergent module through a narrow intent, explicit bindings to peer behavior IDs, a concrete scenario, and a test item that cites every behavior it verifies.

## Adaptation for this project

- Each module receives a stable kebab-case package ID.
- Each behavior receives a permanent `<package>-<number>` ID.
- Each statement expresses one requirement using condition, trigger, subject, and required outcome.
- Each dependency on another module cites the exact behavior ID it consumes.
- Each verification item cites the behaviors it proves.
- Integration and end-to-end behavior live in the module or a narrowly scoped composition package.
- Unit tests remain implementation details referenced from `TEST-MATRIX.md`; behavior specs focus on externally meaningful guarantees.
- Decision records preserve consequential design choices.
- Intent records track disposable implementation work and cannot become a substitute for durable behavior specs.

## GSD mapping

| Spex concept | GSD/project artifact |
| --- | --- |
| Spec map | `.planning/ROADMAP.md` and requirement traceability matrix |
| Package intent | Phase `SPEC.md` and `INTENT.md` |
| External/Internal Behavior | Phase `SPEC.md` and `DESIGN.md` |
| Verification | Phase `TEST-MATRIX.md` and `VERIFICATION.md` |
| Decision record | Phase `DECISIONS.md` and project decision index |
| Intent record | Phase `INTENT.md` and `TODO.md` |
| Cross-package binding | Explicit dependency and behavior-ID citation |

## Authoring rules

- Prefer concise tables, bullets, and diagrams over loose prose.
- Keep one contract per behavior ID.
- Preserve released IDs rather than renumbering them.
- Write self-contained behavior statements; citations identify dependencies but do not carry the statement's meaning.
- Completion is not inferred from checked deliverables alone; verification evidence and an empty scoped TODO set are both required.

