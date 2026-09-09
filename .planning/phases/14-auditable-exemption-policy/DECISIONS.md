# Decisions

- DEC-P14-001: Use `exempt_rules` instead of a new parallel table, because V1 already introduced this model for F-3.6.
- DEC-P14-002: Non-exemptable controls always win over matching active exemptions.
- DEC-P14-003: For overlapping active policies, the newest higher version wins deterministically.
- DEC-P14-004: Chrome-only Playwright remains the production browser verification boundary.
