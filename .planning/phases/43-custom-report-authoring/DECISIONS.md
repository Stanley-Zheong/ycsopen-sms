# Phase 43 Decisions

- Use existing Phase34 aggregate registry/table instead of introducing another metric store.
- Limit Phase43 authoring to whitelisted dimensions/measures to prevent SQL injection and unsupported reports.
- Store immutable JSON snapshots at save/export-request time so later registry changes do not alter historical definitions.
- Validate only local Chrome for browser automation.
- Pencil MCP was unavailable in this run; the phase records a minimal `.pen` source marker plus HTML/React/Playwright evidence.
- Expose only metrics present in both supported dimension and supported measure registries; active DB registry rows without an authoring contract stay hidden.
- Submit all supported measures for the selected metric instead of silently truncating to a fixed count.
- Force tenant-authenticated report definitions to `roleScope=TENANT` even if the client submits `PLATFORM`.
