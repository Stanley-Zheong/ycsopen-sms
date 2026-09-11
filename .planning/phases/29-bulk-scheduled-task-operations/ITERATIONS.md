# Phase 29 Iterations

- Iteration 1: Added backend migration/service/controller; focused test exposed H2 generated-key ambiguity and was fixed by requesting only `id`.
- Iteration 2: Added frontend pages/API/routes/styles; unit test exposed missing external batch key in admin table and the page was corrected.
- Iteration 3: Local Chrome Playwright exposed a mock route mismatch for `/console/bulk/tasks`; the test fixture was corrected.
- Iteration 4: Claude review found that scheduled tasks dispatched immediately, preview/snapshot carried raw phone fields, create used one rollback-prone transaction, and several control paths lacked executable tests. Fixed by making scheduled create PENDING without submit, exposing only masked preview rows, removing the outer create transaction, failing recorded batches on mid-loop exceptions, enforcing import metadata, and adding scheduled/CANCEL/FAIL/RESTART/partial-failure tests.
- Iteration 5: Closure review confirmed the prior blocker/high findings were addressed. Full backend/frontend verification was rerun after the fixes.
