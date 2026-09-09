# Phase 26 Context

Package: `tenant-console-send`

Phase26 turns the tenant console send placeholder into a real authenticated console-send surface. It reuses existing approved template/signature APIs, template preview, and the Phase23/24 acceptance pipeline through a thin JWT adapter.

Scoped owner obligations:

- OBL-F-6-10-A
- OBL-F-6-10-B
- OBL-EDGE-NETWORK-TIMEOUT

Out of scope:

- File bulk import.
- HMAC API changes.
- New billing/routing/delivery logic.
- Browser coverage beyond local Chrome.
