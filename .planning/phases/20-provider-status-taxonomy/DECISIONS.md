# Decisions

| ID | Decision | Rationale |
| --- | --- | --- |
| P20-D01 | Use a single `ProviderStatusTaxonomyPort` for HTTP and CMPP consumers. | Prevents two protocol-specific taxonomies from diverging. |
| P20-D02 | Unknown codes are safe by default. | Unknown provider statuses must not fabricate finality, billability, or automatic retry. |
| P20-D03 | Import validates at least one row before superseding current active data. | Prevents a fully invalid import from deleting the active contract. |
| P20-D04 | Export is request-registration only. | Real async export delivery belongs to the later export phase. |

