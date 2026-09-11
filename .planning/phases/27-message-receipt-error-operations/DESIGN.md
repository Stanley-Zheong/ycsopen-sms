# Phase 27 Design

The admin records operations page is a four-section operations console:

1. Submission detail: acceptance source, tenant, submitId, messageId, status, template/signature, and rejection reason.
2. Send detail: masked recipient, content summary, state, channel/provider, carrier/location, cost/retry, and resend/appeal actions.
3. Receipt detail: original receipt rows, channel/provider IDs, status, error, raw payload summary, correction and replay actions.
4. Error detail: normalized failure groups by code/type/severity/retryability with bulk retry and problem marking.

Visual style follows the existing ycsan-like web implementation: page header, compact filters, card sections, dense tables, green success feedback, and red error feedback.
