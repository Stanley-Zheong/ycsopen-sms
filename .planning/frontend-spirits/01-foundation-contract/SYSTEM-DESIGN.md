# Spirit 01 System Design

## Component Ownership

- `web` shared layout owns shell, sidebar, breadcrumb, page header, and content container behavior.
- Shared query components own label placement, field width, action row, collapse, reset, loading, and result refresh semantics.
- Shared form components own create/edit modal or drawer behavior, validation display, submit, cancel, close, and unsaved-change confirmation.
- Shared table components own header, loading, empty, error, result, pagination, and action-column alignment.
- Shared action-confirmation components own target/effect display, reason field, duplicate-submission latch, focus lock, cancellation, retry, and payload snapshot.

## Data Flow

User input flows into a typed query or form state object, then into a page-owned API adapter. Shared components do not invent business parameters; they expose structured submit/reset/confirm events to page owners.

## Command Flow

State-changing commands must pass through confirmation unless the owning spec explicitly declares the action as immediate and no reason-bearing side effect exists.

## Failure Model

- Validation errors stay beside fields and preserve user input.
- Business rejections stay in the action or form context.
- System failures provide retry without duplicating already latched submissions.

## Verification Model

Unit tests prove shared component state transitions. Chrome Playwright proves at least one representative route for query, form, table, and action confirmation behavior.
