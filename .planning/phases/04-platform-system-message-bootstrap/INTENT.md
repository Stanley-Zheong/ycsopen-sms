# Intent

## Status

Open

## Goal

Make registration and operational system messages executable through a controlled platform bootstrap provider path before tenant notification channels are introduced.

## Deliverables

- [ ] Build adapter + SPI entry points for platform bootstrap notifications — Evidence: not recorded
- [ ] Implement and verify delivery evidence, recursion guard, and audit traces — Evidence: not recorded
- [ ] Add minimal integration tests for failure/retry/guard behavior — Evidence: not recorded

## Tasks

1. Add phase-owned adapter interfaces and delivery service.
2. Add recipient/template normalization and non-secret provider result model.
3. Add integration tests for success/failure, recursion, and retry classification.

## Verification

Planned: `npx playwright` smoke is not mandatory in this phase and full delivery proof will use `mvn`/`npm` + local fixture tests first; browser checks only for evidence that the real provider sandbox route returns correctly.

