# Decisions

- Domestic sends must reference a template. Free text is rejected by `TemplateSendComplianceService`.
- Variables are declared by `${name}` placeholders in template content.
- Parameter rules use the minimal explicit grammar `name:digits(min-max)`, `name:number(min-max)`, or `name:text(min-max)`, separated by semicolons.
- Resubmission creates a new pending row linked to the previous template instead of mutating rejected evidence.
- Batch/CMPP conformance is documented as adapter obligation against the shared validator; transports are not invented in this phase.
