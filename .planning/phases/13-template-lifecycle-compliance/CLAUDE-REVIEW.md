# Claude Review

## Command

`claude -p --output-format json --disable-slash-commands --tools ""`

Scope: Phase 13 code/test diff under `core/src/main`, `core/src/test`, `web/src`, and `web/test`.

## Findings

- BLOCKER reported for possible template/signature binding bypass in `MessageSubmitService.submit`.
  - Disposition: Not accepted. `TemplateSendComplianceService.validateDomesticSend` rejects a provided `signId` that does not equal `template.getSignatureId()` and then loads the signature by the template binding.
  - Evidence: `core/src/main/java/com/ycsopen/sms/core/service/template/TemplateSendComplianceService.java`; `TemplateSendComplianceServiceTest`.
- HIGH reported for `MessageSubmitService` manually constructing `TemplateSendComplianceService`.
  - Disposition: Fixed. `MessageSubmitService` now receives `TemplateSendComplianceService` through constructor injection.
  - Evidence: `core/src/main/java/com/ycsopen/sms/core/service/message/MessageSubmitService.java`; `MessageSubmitServiceTest`.
- BLOCKER reported in staged review for `V2200__template_lifecycle_compliance.sql` not adding `template_name`, `param_check_rule`, `description`, `audit_time`, `audit_comment`, or `usage_count`.
  - Disposition: Not accepted. Those columns already exist in `V1__init_schema.sql`; V2200 only adds Phase 13 delta columns `variable_names`, `version_no`, `previous_template_id`, unique successor constraint, and `AMENDMENT_REQUIRED`.
  - Evidence: `core/src/main/resources/db/migration/V1__init_schema.sql`; `core/src/main/resources/db/migration/V2200__template_lifecycle_compliance.sql`; `mvn -q -f core/pom.xml test`.

## Post-fix verification

`mvn -q -f core/pom.xml -Dtest=TemplateLifecycleServiceTest,TemplateSendComplianceServiceTest,MessageSubmitServiceTest test` passed.

## Verdict

No unresolved BLOCKER/HIGH findings remain from Claude review.
