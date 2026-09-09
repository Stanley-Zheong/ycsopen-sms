# Phase 23 Intent

The acceptance endpoint should be safe to expose to tenant developers:

- authentication proves the request body was not changed;
- replay and disallowed source IPs stop before business code;
- retries by `submitId` are stable;
- accepted work is durable before a message ID is returned;
- rejection paths do not leak tasks, send intents, or charges.
