# Phase 25 Decisions

## D1. Reuse channel health page

The recovery UI is added to `/admin/channel/health` instead of creating a new route. This keeps the operator workflow tied to the channel state that triggered recovery and avoids extra navigation or menu work.

## D2. Migrate only READY/PENDING

Automatic migration is restricted to tasks that are still PENDING and have a READY outbox row. CLAIMED rows with provider error evidence are treated as uncertain outcome and must be reconciled before retry.

## D3. Retry creates a new task

FAILED retry creates a new PENDING task and outbox row. The original FAILED record remains unchanged so history and billing/reconciliation evidence are preserved.

## D4. Recovery requires evidence

Every migrate, retry, test, and resume action requires actor and evidence text. Paused channel resume requires the latest recovery test to be successful.

## D5. Chrome-only verification

UI automation uses the local Google Chrome binary configured by `web/playwright.config.ts`. No Chrome download, Edge, Safari, or mobile validation is required for this project.
