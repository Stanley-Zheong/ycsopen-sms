#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "fileutils"
require "json"
require "open3"
require "securerandom"
require "timeout"

require_relative "../../../.planning/tools/verification-evidence"

module Phase01RunChecks
  class ConfigurationError < StandardError; end

  PHASE_DIR = ".planning/phases/01-engineering-verification-foundation"
  EVIDENCE_DIR = ".planning/phases/01-engineering-verification-foundation/EVIDENCE"
  LOCAL_CHROME_RUNTIME = ".planning/phases/01-engineering-verification-foundation/EVIDENCE/local-chrome-runtime.json"
  DURABLE_SUBJECT = ".planning/phases/01-engineering-verification-foundation/EVIDENCE/tested-inputs.json"
  OBLIGATION_EVIDENCE_MANIFEST = ".planning/phases/01-engineering-verification-foundation/EVIDENCE/evidence-manifest.json"
  PORTABLE_RUNTIME_MAX_BYTES = 32 * 1024 * 1024
  PORTABLE_JSON_MAX_BYTES = 4 * 1024 * 1024
  PORTABLE_STDIN_MAX_BYTES = PORTABLE_RUNTIME_MAX_BYTES + (2 * PORTABLE_JSON_MAX_BYTES) + (64 * 1024)
  SCENARIO_VALIDATOR_PATH = "web/scripts/test-browser-scenario-validator.mjs"
  SCENARIO_VALIDATOR_DEPENDENCY_PATH = "web/scripts/validate-browser-scenario.mjs"
  SCENARIO_SERVER_PATH = "web/scripts/test-browser-scenario-server.mjs"
  SCENARIO_PROBE_PATH = "web/scripts/probe-local-chrome.mjs"
  SCENARIO_SERVE_PATH = "web/scripts/serve-browser-scenario.mjs"
  SCENARIO_LOCAL_CHROME_PATH = "web/scripts/test-browser-scenario-visual-local-chrome.mjs"
  LOCAL_CHROME_FLAG = "--run-local-chrome"
  RUN_PLAYWRIGHT_FLAG = "--run-playwright"
  SCENARIO_CONTRACT_ARGV = ["/usr/bin/env", "node", SCENARIO_VALIDATOR_PATH].freeze
  SCENARIO_SERVER_ARGV = ["/usr/bin/env", "node", SCENARIO_SERVER_PATH].freeze
  SCENARIO_LOCAL_CHROME_ARGV = ["/usr/bin/env", "node", SCENARIO_LOCAL_CHROME_PATH, LOCAL_CHROME_FLAG].freeze
  CI_SCENARIO_SOURCE_SHA256 = {
    SCENARIO_VALIDATOR_PATH => "0f557d3b27639623e5be81c12dfd80cceef23f2d19a4fe533d092f2d213e70f0",
    SCENARIO_VALIDATOR_DEPENDENCY_PATH => "026d21b5716e3c293cf1ad45a0c0ff856c1013810277f0aae40dadf6a85609eb",
    SCENARIO_SERVER_PATH => "ff6f1dddd316a9dceb75553800e956692790f5d0f2f0deeeae24fc0c7886c779",
    SCENARIO_PROBE_PATH => "9f0179270e857f05cb882a35394ace896611ddc21e577e3b35b07ddaf1f6e4e5",
    SCENARIO_SERVE_PATH => "a67c65346e8abd68bd131e1551b6d15f1f7cb5fc7d056b1c52dbc1376d0d80d8"
  }.freeze

  RUNNER_INPUTS = [
    { "path" => ".planning/tools/verification-evidence.rb", "role" => "implementation" },
    { "path" => ".planning/tools/validate-verification-evidence.rb", "role" => "validator" },
    { "path" => ".planning/tools/test-repository-verification.rb", "role" => "test" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/EVIDENCE/schema/evidence-envelope.schema.json", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/EVIDENCE/schema/aggregate.schema.json", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/EVIDENCE/schema/tested-inputs.schema.json", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/EVIDENCE/schema/obligation-summary.schema.json", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/EVIDENCE/schema/obligation-evidence-manifest.schema.json", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/EVIDENCE/fixtures/evidence-mutations.json", "role" => "test" },
    { "path" => ".planning/tools/produce-phase-01-obligation-evidence.rb", "role" => "implementation" },
    { "path" => ".planning/tools/test-phase-01-obligation-evidence.rb", "role" => "test" },
    { "path" => "scripts/verify-phase-01", "role" => "implementation" },
    { "path" => "scripts/lib/phase-01/run_checks.rb", "role" => "implementation" },
    { "path" => "scripts/lib/phase-01/test_run_checks.rb", "role" => "test" },
    { "path" => ".github/workflows/ci.yml", "role" => "config" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-VALIDATION.md", "role" => "contract" }
  ].freeze

  TRACE_INPUTS = [
    { "path" => ".planning/PRD-OBLIGATIONS.md", "role" => "contract" },
    { "path" => ".planning/REQUIREMENTS.md", "role" => "contract" },
    { "path" => ".planning/ROADMAP.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-SPEC.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/TEST-MATRIX.md", "role" => "contract" },
    { "path" => ".planning/tools/planning-validator-support.rb", "role" => "implementation" },
    { "path" => ".planning/tools/validate-prd-obligations.rb", "role" => "validator" },
    { "path" => ".planning/tools/validate-trace-closure.rb", "role" => "validator" },
    { "path" => ".planning/tools/test-trace-closure.rb", "role" => "test" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-00-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-01-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-02-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-03-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-04-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-05-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-06-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-07-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-08-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-09-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-10-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-11-PLAN.md", "role" => "contract" },
    { "path" => ".planning/phases/01-engineering-verification-foundation/01-12-PLAN.md", "role" => "contract" }
  ].freeze

  LIFECYCLE_INPUTS = [
    { "path" => ".planning/tools/validate-phase-lifecycle.rb", "role" => "validator" },
    { "path" => ".planning/tools/test-phase-lifecycle.rb", "role" => "test" },
    { "path" => ".planning/tools/validate-delivery-attestation.rb", "role" => "validator" },
    { "path" => ".planning/tools/test-delivery-attestation.rb", "role" => "test" },
    { "path" => ".planning/tools/validate-phase-entry.rb", "role" => "validator" },
    { "path" => ".planning/tools/validate-ui-contract.rb", "role" => "validator" },
    { "path" => ".planning/tools/test-planning-validators.rb", "role" => "test" },
    { "path" => ".planning/PHASE-ARTIFACT-TEMPLATE.md", "role" => "contract" },
    { "path" => ".planning/tools/bootstrap-phase-01.rb", "role" => "validator" },
    { "path" => ".planning/tools/test-bootstrap-phase-01.rb", "role" => "test" },
    { "path" => ".planning/tools/phase01-chrome-entry-contract.rb", "role" => "contract" },
    { "path" => ".planning/tools/produce-phase-01-chrome-entry.rb", "role" => "implementation" },
    { "path" => ".planning/tools/test-produce-phase-01-chrome-entry.rb", "role" => "test" }
  ].freeze

  CORE_INPUTS = [
    { "path" => "core/pom.xml", "role" => "config" },
    { "path" => "core/src/main/resources/application.yml", "role" => "config" },
    { "path" => "core/src/main/resources/application-dev.yml", "role" => "config" },
    { "path" => "core/src/main/resources/db/migration/V1__init_schema.sql", "role" => "implementation" },
    { "path" => "core/src/test/resources/application-test.yml", "role" => "config" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/YcsopenSmsCoreApplication.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/cmpp/package-info.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/common/exception/BusinessException.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/common/exception/GlobalExceptionHandler.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/common/security/FieldEncryptor.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/common/security/HashUtil.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/common/security/HmacSignatureVerifier.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/common/security/JwtTokenProvider.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/config/SecurityConfig.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/config/WebMvcConfig.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/BillingRecord.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/BlacklistEntry.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/Channel.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/Complaint.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/ComplaintRatioStats.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/FrequencyRule.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/MessageTask.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/RouteRule.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/SensitiveWord.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/Signature.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/Template.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/Tenant.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/TenantAccount.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/TenantApiKey.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/domain/entity/User.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/BillingRecordRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/BlacklistEntryRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/ChannelRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/ComplaintRatioStatsRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/ComplaintRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/FrequencyRuleRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/MessageTaskRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/RouteRuleRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/SensitiveWordRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/SignatureRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/TemplateRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/TenantAccountRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/TenantApiKeyRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/TenantRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/repository/UserRepository.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/account/AuthService.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/alert/package-info.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/billing/BillingService.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/channel/package-info.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/complaint/ComplaintRatioService.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/message/MessageSubmitService.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/routing/BlacklistChecker.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/routing/ChannelSelector.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/routing/ContentReviewChecker.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/routing/FrequencyChecker.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/routing/RoutingContext.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/routing/RoutingDecision.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/routing/RoutingEngine.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/routing/ThirdPartyBlacklistClient.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/signature/package-info.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/tenant/TenantService.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/service/tool/package-info.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/controller/AuthController.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/controller/ChannelController.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/controller/DashboardController.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/controller/MessageController.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/controller/TenantController.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/dto/ApiResponse.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/dto/ComplaintRatioItemResponse.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/dto/LoginRequest.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/dto/LoginResponse.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/dto/SmsSendRequest.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/dto/SmsSendResponse.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/dto/TenantRegistrationRequest.java", "role" => "implementation" },
    { "path" => "core/src/main/java/com/ycsopen/sms/core/web/interceptor/HmacAuthInterceptor.java", "role" => "implementation" },
    { "path" => "core/src/test/java/com/ycsopen/sms/core/common/security/FieldEncryptorTest.java", "role" => "test" },
    { "path" => "core/src/test/java/com/ycsopen/sms/core/service/billing/BillingServiceTest.java", "role" => "test" },
    { "path" => "core/src/test/java/com/ycsopen/sms/core/service/complaint/ComplaintRatioServiceTest.java", "role" => "test" },
    { "path" => "core/src/test/java/com/ycsopen/sms/core/service/routing/RoutingEngineTest.java", "role" => "test" },
    { "path" => "core/src/test/java/com/ycsopen/sms/core/web/HmacSignatureVerifierTest.java", "role" => "test" }
  ].freeze

  WEB_INPUTS = [
    { "path" => "web/package.json", "role" => "config" },
    { "path" => "web/package-lock.json", "role" => "config" },
    { "path" => "web/.eslintrc.cjs", "role" => "config" },
    { "path" => "web/tsconfig.json", "role" => "config" },
    { "path" => "web/vite.config.ts", "role" => "config" },
    { "path" => "web/index.html", "role" => "config" },
    { "path" => "web/playwright.config.ts", "role" => "config" },
    { "path" => "web/lib/format.ts", "role" => "implementation" },
    { "path" => "web/src/api/auth.ts", "role" => "implementation" },
    { "path" => "web/src/api/channels.ts", "role" => "implementation" },
    { "path" => "web/src/api/client.ts", "role" => "implementation" },
    { "path" => "web/src/api/dashboard.ts", "role" => "implementation" },
    { "path" => "web/src/api/tenants.ts", "role" => "implementation" },
    { "path" => "web/src/app/App.tsx", "role" => "implementation" },
    { "path" => "web/src/components/common/PlaceholderPage.tsx", "role" => "implementation" },
    { "path" => "web/src/components/layout/AdminLayout.tsx", "role" => "implementation" },
    { "path" => "web/src/components/layout/TenantLayout.tsx", "role" => "implementation" },
    { "path" => "web/src/main.tsx", "role" => "implementation" },
    { "path" => "web/src/pages/LoginPage.tsx", "role" => "implementation" },
    { "path" => "web/src/pages/admin/channels/ChannelListPage.tsx", "role" => "implementation" },
    { "path" => "web/src/pages/admin/dashboard/ComplaintRatioPanel.tsx", "role" => "implementation" },
    { "path" => "web/src/pages/admin/dashboard/DashboardPage.tsx", "role" => "implementation" },
    { "path" => "web/src/pages/admin/tenants/TenantListPage.tsx", "role" => "implementation" },
    { "path" => "web/src/pages/tenant/overview/OverviewPage.tsx", "role" => "implementation" },
    { "path" => "web/src/pages/tenant/send/SendPage.tsx", "role" => "implementation" },
    { "path" => "web/src/router/routes.tsx", "role" => "implementation" },
    { "path" => "web/src/store/authStore.ts", "role" => "implementation" },
    { "path" => "web/src/styles/index.css", "role" => "implementation" },
    { "path" => "web/src/types/api.ts", "role" => "implementation" },
    { "path" => "web/test/setup.ts", "role" => "test" },
    { "path" => "web/test/unit/format.test.ts", "role" => "test" }
  ].freeze

  UI_INPUTS = [
    { "path" => "web/scripts/validate-ui-drift.mjs", "role" => "validator" },
    { "path" => "web/scripts/test-ui-drift-validator.mjs", "role" => "test" },
    { "path" => "web/verification/ui-manifest.json", "role" => "contract" },
    { "path" => "web/verification/ui-manifest.schema.json", "role" => "contract" },
    { "path" => "web/verification/row-key-registry.json", "role" => "contract" },
    { "path" => "web/verification/row-key-registry.schema.json", "role" => "contract" },
    { "path" => "web/verification/fixtures/ui-drift-cases.json", "role" => "test" },
    { "path" => "web/src/pages/LoginPage.tsx", "role" => "implementation" },
    { "path" => "web/src/router/routes.tsx", "role" => "implementation" },
    { "path" => "web/test/phase01/login-scenario.spec.ts", "role" => "test" }
  ].freeze

  SCENARIO_INPUTS = [
    { "path" => "web/scripts/probe-local-chrome.mjs", "role" => "implementation" },
    { "path" => "web/scripts/serve-browser-scenario.mjs", "role" => "implementation" },
    { "path" => "web/scripts/test-browser-scenario-server.mjs", "role" => "test" },
    { "path" => "web/scripts/validate-browser-scenario.mjs", "role" => "validator" },
    { "path" => "web/scripts/test-browser-scenario-validator.mjs", "role" => "test" },
    { "path" => "web/scripts/test-browser-scenario-visual-local-chrome.mjs", "role" => "test" },
    { "path" => "web/verification/browser-scenarios.json", "role" => "contract" },
    { "path" => "web/verification/browser-scenarios.schema.json", "role" => "contract" },
    { "path" => "web/test/phase01/login-scenario.spec.ts", "role" => "test" }
  ].freeze

  COPY_INPUTS = [
    { "path" => "web/scripts/validate-copy-zh-cn.mjs", "role" => "validator" },
    { "path" => "web/scripts/test-copy-zh-cn.mjs", "role" => "test" },
    { "path" => "web/verification/copy.zh-CN.json", "role" => "contract" },
    { "path" => "web/verification/fixtures/zh-cn-export.csv", "role" => "test" },
    { "path" => "web/test/phase01/chinese-copy.spec.ts", "role" => "test" },
    { "path" => "web/src/pages/LoginPage.tsx", "role" => "implementation" },
    { "path" => "web/playwright.config.ts", "role" => "config" },
    { "path" => "web/package.json", "role" => "config" }
  ].freeze

  SERVICE_INPUTS = [
    { "path" => "scripts/lib/phase-01/service_checks.rb", "role" => "implementation" },
    { "path" => "scripts/lib/phase-01/test_service_checks.rb", "role" => "test" },
    { "path" => "core/src/test/resources/application-phase01-integration.yml", "role" => "config" },
    { "path" => "core/src/test/resources/verification/timezone-contract.json", "role" => "contract" },
    { "path" => "core/src/test/java/com/ycsopen/sms/core/verification/Phase01MySqlIntegrationTest.java", "role" => "test" },
    { "path" => "core/src/test/java/com/ycsopen/sms/core/verification/Phase01RedisIntegrationTest.java", "role" => "test" },
    { "path" => "core/src/test/java/com/ycsopen/sms/core/verification/Phase01TimezoneContractTest.java", "role" => "test" },
    { "path" => "core/pom.xml", "role" => "config" },
    { "path" => "core/src/main/resources/db/migration/V1__init_schema.sql", "role" => "implementation" }
  ].freeze

  LOCAL_CHROME_INPUTS = [
    { "path" => "web/scripts/probe-local-chrome.mjs", "role" => "implementation" },
    { "path" => "web/scripts/run-local-chrome-smoke.mjs", "role" => "implementation" },
    { "path" => "web/scripts/validate-local-chrome-evidence.mjs", "role" => "validator" },
    { "path" => "web/scripts/test-local-chrome-evidence.mjs", "role" => "test" },
    { "path" => "web/verification/local-chrome-runtime.schema.json", "role" => "contract" },
    { "path" => "web/verification/browser-scenarios.json", "role" => "contract" },
    { "path" => "web/playwright.config.ts", "role" => "config" }
  ].freeze

  TRACE_004_CHILD_ARGVS = [
    ["/usr/bin/env", "ruby", ".planning/tools/test-phase-lifecycle.rb"],
    ["/usr/bin/env", "ruby", ".planning/tools/test-delivery-attestation.rb"]
  ].freeze

  CHECKS = [
    {
      "id" => "trace-closure-001",
      "layer" => "catalog",
      "argv" => ["/usr/bin/env", "ruby", ".planning/tools/test-trace-closure.rb", "--case", "CASE-FOUND-TRACE-001"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-001"],
      "case_ids" => ["CASE-FOUND-TRACE-001"],
      "inputs" => TRACE_INPUTS,
      "timeout_seconds" => 120,
      "output_contract" => "process",
      "scopes" => %w[self-test ci all]
    },
    {
      "id" => "trace-closure-002",
      "layer" => "catalog",
      "argv" => ["/usr/bin/env", "ruby", ".planning/tools/test-trace-closure.rb", "--case", "CASE-FOUND-TRACE-002"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-002"],
      "case_ids" => ["CASE-FOUND-TRACE-002"],
      "inputs" => TRACE_INPUTS,
      "timeout_seconds" => 120,
      "output_contract" => "process",
      "scopes" => %w[self-test ci all]
    },
    {
      "id" => "evidence-kernel-self-test",
      "layer" => "validator",
      "argv" => ["/usr/bin/env", "ruby", ".planning/tools/test-repository-verification.rb"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => RUNNER_INPUTS,
      "timeout_seconds" => 120,
      "output_contract" => "process",
      "scopes" => %w[self-test ci all]
    },
    {
      "id" => "phase-lifecycle-delivery",
      "layer" => "lifecycle",
      "argv" => ["/usr/bin/env", "ruby", "scripts/lib/phase-01/run_checks.rb", "--internal-trace-004"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-004"],
      "case_ids" => ["CASE-FOUND-TRACE-004"],
      "inputs" => LIFECYCLE_INPUTS,
      "timeout_seconds" => 300,
      "output_contract" => "process",
      "scopes" => %w[self-test ci all]
    },
    {
      "id" => "core-unit",
      "layer" => "unit",
      "argv" => ["/usr/bin/env", "mvn", "-f", "core/pom.xml", "test"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => CORE_INPUTS,
      "timeout_seconds" => 600,
      "output_contract" => "process",
      "scopes" => %w[ci all]
    },
    {
      "id" => "web-install",
      "layer" => "dependency",
      "argv" => ["/usr/bin/env", "npm", "--prefix", "web", "ci"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => WEB_INPUTS,
      "timeout_seconds" => 600,
      "output_contract" => "process",
      "scopes" => %w[ci all]
    },
    {
      "id" => "web-lint",
      "layer" => "static",
      "argv" => ["/usr/bin/env", "npm", "--prefix", "web", "run", "lint"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => WEB_INPUTS,
      "timeout_seconds" => 300,
      "output_contract" => "process",
      "scopes" => %w[ci all]
    },
    {
      "id" => "web-unit",
      "layer" => "unit",
      "argv" => ["/usr/bin/env", "npm", "--prefix", "web", "test"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => WEB_INPUTS,
      "timeout_seconds" => 300,
      "output_contract" => "process",
      "scopes" => %w[ci all]
    },
    {
      "id" => "web-build",
      "layer" => "build",
      "argv" => ["/usr/bin/env", "npm", "--prefix", "web", "run", "build"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => WEB_INPUTS,
      "timeout_seconds" => 300,
      "output_contract" => "process",
      "scopes" => %w[ci all]
    },
    {
      "id" => "ui-drift",
      "layer" => "static",
      "argv" => ["/usr/bin/env", "node", "web/scripts/test-ui-drift-validator.mjs"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-UI-DRIFT-001", "OBL-FOUND-UI-DRIFT-002"],
      "case_ids" => ["CASE-FOUND-UI-DRIFT-001", "CASE-FOUND-UI-DRIFT-002"],
      "inputs" => UI_INPUTS,
      "timeout_seconds" => 180,
      "output_contract" => "process",
      "scopes" => %w[ci all]
    },
    {
      "id" => "login-scenario-server",
      "layer" => "integration",
      "argv" => SCENARIO_SERVER_ARGV,
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => SCENARIO_INPUTS,
      "timeout_seconds" => 180,
      "output_contract" => "process",
      "scopes" => %w[ci all]
    },
    {
      "id" => "login-scenario-contract",
      "layer" => "validator",
      "argv" => SCENARIO_CONTRACT_ARGV,
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => SCENARIO_INPUTS,
      "timeout_seconds" => 180,
      "output_contract" => "process",
      "scopes" => %w[ci]
    },
    {
      "id" => "login-scenario-visual-local-chrome",
      "layer" => "browser",
      "argv" => SCENARIO_LOCAL_CHROME_ARGV,
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => SCENARIO_INPUTS,
      "timeout_seconds" => 180,
      "output_contract" => "process",
      "scopes" => %w[all]
    },
    {
      "id" => "copy-static",
      "layer" => "validator",
      "argv" => ["/usr/bin/env", "node", "web/scripts/validate-copy-zh-cn.mjs", "--registry", "web/verification/copy.zh-CN.json", "--export-fixture", "web/verification/fixtures/zh-cn-export.csv", "--source", "web/src/pages/LoginPage.tsx"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => COPY_INPUTS,
      "timeout_seconds" => 120,
      "output_contract" => "process",
      "scopes" => %w[ci]
    },
    {
      "id" => "copy-mutations",
      "layer" => "validator",
      "argv" => ["/usr/bin/env", "node", "web/scripts/test-copy-zh-cn.mjs"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => COPY_INPUTS,
      "timeout_seconds" => 180,
      "output_contract" => "process",
      "scopes" => %w[ci]
    },
    {
      "id" => "copy-local-browser",
      "layer" => "browser",
      "argv" => ["/usr/bin/env", "npm", "--prefix", "web", "run", "test:copy:zh-cn"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => COPY_INPUTS,
      "timeout_seconds" => 300,
      "output_contract" => "process",
      "scopes" => %w[all]
    },
    {
      "id" => "mysql-real",
      "layer" => "integration",
      "argv" => ["/usr/bin/env", "ruby", "scripts/lib/phase-01/test_service_checks.rb", "--mysql"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => SERVICE_INPUTS,
      "timeout_seconds" => 900,
      "output_contract" => "process",
      "scopes" => %w[ci all]
    },
    {
      "id" => "redis-real",
      "layer" => "integration",
      "argv" => ["/usr/bin/env", "ruby", "scripts/lib/phase-01/test_service_checks.rb", "--redis"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => SERVICE_INPUTS,
      "timeout_seconds" => 600,
      "output_contract" => "process",
      "scopes" => %w[ci all]
    },
    {
      "id" => "timezone-contract",
      "layer" => "integration",
      "argv" => ["/usr/bin/env", "ruby", "scripts/lib/phase-01/test_service_checks.rb", "--timezone-contract"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => SERVICE_INPUTS,
      "timeout_seconds" => 120,
      "output_contract" => "process",
      "scopes" => %w[ci all timezone]
    },
    {
      "id" => "service-java-integration",
      "layer" => "integration",
      "argv" => ["/usr/bin/env", "mvn", "-f", "core/pom.xml", "-Pphase01-integration", "-Dtest=Phase01MySqlIntegrationTest,Phase01RedisIntegrationTest,Phase01TimezoneContractTest", "test"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => SERVICE_INPUTS,
      "timeout_seconds" => 1200,
      "output_contract" => "process",
      "scopes" => %w[ci all]
    },
    {
      "id" => "local-chrome-contract-fixtures",
      "layer" => "validator",
      "argv" => ["/usr/bin/env", "node", "web/scripts/test-local-chrome-evidence.mjs"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => LOCAL_CHROME_INPUTS,
      "timeout_seconds" => 180,
      "output_contract" => "process",
      "scopes" => %w[ci all]
    },
    {
      "id" => "local-chrome-artifact-portable",
      "layer" => "validator",
      "argv" => ["/usr/bin/env", "ruby", "scripts/lib/phase-01/run_checks.rb", "--internal-portable-chrome-artifact"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-FOUND-TRACE-003"],
      "case_ids" => ["CASE-FOUND-TRACE-003"],
      "inputs" => LOCAL_CHROME_INPUTS,
      "timeout_seconds" => 180,
      "output_contract" => "process",
      "scopes" => %w[ci]
    },
    {
      "id" => "local-chrome-runtime",
      "layer" => "browser",
      "argv" => ["/usr/bin/env", "ruby", "scripts/lib/phase-01/run_checks.rb", "--internal-local-chrome-runtime"],
      "cwd" => ".",
      "obligation_ids" => ["OBL-NFR-BROWSER"],
      "case_ids" => ["CASE-NFR-BROWSER"],
      "inputs" => LOCAL_CHROME_INPUTS,
      "timeout_seconds" => 180,
      "output_contract" => "process",
      "scopes" => %w[all]
    }
  ].freeze

  module_function

  def definitions_for(scopes)
    CHECKS.select { |definition| !(definition.fetch("scopes") & scopes).empty? }
  end

  def scenario_check_contract!(definitions)
    contract = definitions.select { |definition| definition["id"] == "login-scenario-contract" }
    server = definitions.select { |definition| definition["id"] == "login-scenario-server" }
    local = definitions.select { |definition| definition["id"] == "login-scenario-visual-local-chrome" }
    unless contract.length == 1 && contract.first.values_at("layer", "argv", "scopes") == ["validator", SCENARIO_CONTRACT_ARGV, ["ci"]]
      raise ConfigurationError, "CHECK_SCENARIO_PORTABLE_BINDING_INVALID"
    end
    unless server.length == 1 && server.first.values_at("layer", "argv", "scopes") == ["integration", SCENARIO_SERVER_ARGV, %w[ci all]]
      raise ConfigurationError, "CHECK_SCENARIO_SERVER_BINDING_INVALID"
    end
    unless local.length == 1 && local.first.values_at("layer", "argv", "scopes") == ["browser", SCENARIO_LOCAL_CHROME_ARGV, ["all"]]
      raise ConfigurationError, "CHECK_SCENARIO_LOCAL_CHROME_BINDING_INVALID"
    end

    contract_invocations = definitions.select { |definition| definition["argv"]&.include?(SCENARIO_VALIDATOR_PATH) }
    unless contract_invocations.map { |definition| definition.fetch("id") } == ["login-scenario-contract"]
      raise ConfigurationError, "CHECK_SCENARIO_PORTABLE_INVOCATION_SET_INVALID"
    end
    local_invocations = definitions.select { |definition| definition["argv"]&.include?(SCENARIO_LOCAL_CHROME_PATH) }
    unless local_invocations.map { |definition| definition.fetch("id") } == ["login-scenario-visual-local-chrome"]
      raise ConfigurationError, "CHECK_SCENARIO_LOCAL_CHROME_INVOCATION_SET_INVALID"
    end
    server_invocations = definitions.select { |definition| definition["argv"]&.include?(SCENARIO_SERVER_PATH) }
    unless server_invocations.map { |definition| definition.fetch("id") } == ["login-scenario-server"]
      raise ConfigurationError, "CHECK_SCENARIO_SERVER_INVOCATION_SET_INVALID"
    end
    forbidden_ci = definitions.any? do |definition|
      next false unless definition.fetch("scopes").include?("ci")

      definition.fetch("argv").any? do |argument|
        argument.include?(LOCAL_CHROME_FLAG) || argument.include?(RUN_PLAYWRIGHT_FLAG) ||
          argument.include?(SCENARIO_LOCAL_CHROME_PATH)
      end
    end
    if forbidden_ci
      raise ConfigurationError, "CHECK_CI_LOCAL_CHROME_COMMAND_FORBIDDEN"
    end
    true
  end

  def scenario_validator_source_contract!(source)
    unless source.is_a?(String) && !source.empty?
      raise ConfigurationError, "CHECK_SCENARIO_SOURCE_INVALID"
    end

    forbidden_literals = %w[
      @playwright/test test-browser-scenario-server.mjs
      test-browser-scenario-visual-local-chrome.mjs observeStandardLocalChrome
      --run-local-chrome validateLocalChromeVisualCases evaluateVisualRuleInPage
      chromeBrowserType executablePath
    ]
    forbidden = forbidden_literals.find { |literal| source.include?(literal) }
    if forbidden
      raise ConfigurationError, "CHECK_SCENARIO_CI_BROWSER_SOURCE_FORBIDDEN: #{forbidden}"
    end
    forbidden_primitive = source.match?(/\bimport\s*\(/) ||
      source.match?(/\.launch\s*\(/) || source.match?(/\.newPage\s*\(/) ||
      source.match?(/\.setContent\s*\(/) || source.match?(/\bchromium\b/)
    if forbidden_primitive
      raise ConfigurationError, "CHECK_SCENARIO_CI_BROWSER_PRIMITIVE_FORBIDDEN"
    end
    true
  end

  def exact_ci_scenario_source_contract!(path, source)
    expected = CI_SCENARIO_SOURCE_SHA256.fetch(path) do
      raise ConfigurationError, "CHECK_SCENARIO_SOURCE_PATH_UNREGISTERED: #{path}"
    end
    actual = Digest::SHA256.hexdigest(source)
    unless actual == expected
      raise ConfigurationError, "CHECK_SCENARIO_SOURCE_SHA256_MISMATCH: path=#{path} expected=#{expected} actual=#{actual}"
    end
    true
  end

  def verified_ci_scenario_source!(root, path)
    errors = []
    snapshot = VerificationEvidence.verified_local_file(
      root,
      path,
      errors,
      "CHECK_SCENARIO_SOURCE",
      max_bytes: 256 * 1024
    )
    unless snapshot
      raise ConfigurationError, "#{errors.first || 'CHECK_SCENARIO_SOURCE_UNAVAILABLE'}"
    end
    exact_ci_scenario_source_contract!(path, snapshot.bytes)
    snapshot
  end

  def registry_contract!(root: File.expand_path("../../..", __dir__))
    validate_definitions(CHECKS)
    scenario_check_contract!(CHECKS)
    source_snapshots = CI_SCENARIO_SOURCE_SHA256.keys.to_h do |path|
      [path, verified_ci_scenario_source!(root, path)]
    end
    scenario_validator_source_contract!(source_snapshots.fetch(SCENARIO_VALIDATOR_PATH).bytes)
    local_source_errors = []
    local_source_snapshot = VerificationEvidence.verified_local_file(
      root,
      SCENARIO_LOCAL_CHROME_PATH,
      local_source_errors,
      "CHECK_SCENARIO_LOCAL_CHROME_SOURCE",
      max_bytes: 256 * 1024
    )
    unless local_source_snapshot
      raise ConfigurationError, "#{local_source_errors.first || 'CHECK_SCENARIO_LOCAL_CHROME_SOURCE_UNAVAILABLE'}"
    end
    obligation_ids = CHECKS.flat_map { |definition| definition.fetch("obligation_ids") }.uniq.sort
    expected = %w[
      OBL-FOUND-TRACE-001 OBL-FOUND-TRACE-002 OBL-FOUND-TRACE-003 OBL-FOUND-TRACE-004
      OBL-FOUND-UI-DRIFT-001 OBL-FOUND-UI-DRIFT-002 OBL-NFR-BROWSER
    ].sort
    raise ConfigurationError, "CHECK_OBLIGATION_SET_INVALID: #{obligation_ids.join(',')}" unless obligation_ids == expected

    exact_copy = ["/usr/bin/env", "npm", "--prefix", "web", "run", "test:copy:zh-cn"]
    copy_definition = CHECKS.find { |definition| definition.fetch("argv") == exact_copy }
    raise ConfigurationError, "CHECK_EXACT_COPY_COMMAND_MISSING" unless copy_definition
    unless copy_definition.fetch("scopes") == ["all"] && copy_definition.fetch("obligation_ids") == ["OBL-FOUND-TRACE-003"]
      raise ConfigurationError, "CHECK_EXACT_COPY_SCOPE_INVALID"
    end
    ci_commands = definitions_for(["ci"])
    raise ConfigurationError, "CHECK_CI_BROWSER_COMMAND_FORBIDDEN" if ci_commands.any? { |definition| definition.fetch("layer") == "browser" }
    raise ConfigurationError, "CHECK_CI_EXACT_COPY_FORBIDDEN" if ci_commands.include?(copy_definition)
    unless %w[copy-static copy-mutations local-chrome-contract-fixtures local-chrome-artifact-portable].all? { |id| ci_commands.any? { |definition| definition.fetch("id") == id } }
      raise ConfigurationError, "CHECK_CI_COPY_PORTABLE_MISSING"
    end
    browser_definitions = CHECKS.select { |definition| definition.fetch("obligation_ids").include?("OBL-NFR-BROWSER") }
    unless browser_definitions.length == 1 && browser_definitions.first.fetch("id") == "local-chrome-runtime" && browser_definitions.first.fetch("scopes") == ["all"]
      raise ConfigurationError, "CHECK_BROWSER_BINDING_INVALID"
    end
    true
  end

  def subject_registries(definitions = CHECKS)
    definitions.to_h { |definition| [definition.fetch("id"), definition.fetch("inputs")] }
  end

  def check_contracts(definitions = CHECKS)
    definitions.to_h { |definition| [definition.fetch("id"), definition] }
  end

  def validate_definitions(definitions)
    raise ConfigurationError, "CHECK_SELECTION_EMPTY" unless definitions.is_a?(Array) && !definitions.empty?

    ids = definitions.filter_map { |definition| definition["id"] if definition.is_a?(Hash) }
    duplicate = ids.group_by(&:itself).find { |_id, values| values.length > 1 }&.first
    raise ConfigurationError, "CHECK_ID_DUPLICATE: #{duplicate}" if duplicate

    definitions.each do |definition|
      raise ConfigurationError, "CHECK_DEFINITION_INVALID" unless definition.is_a?(Hash)
      %w[id layer argv cwd obligation_ids case_ids inputs timeout_seconds output_contract].each do |field|
        raise ConfigurationError, "CHECK_FIELD_MISSING: id=#{definition['id']} field=#{field}" unless definition.key?(field)
      end
      id = definition["id"]
      raise ConfigurationError, "CHECK_ID_INVALID: #{id.inspect}" unless id.is_a?(String) && id.match?(VerificationEvidence::CHECK_ID)
      argv = definition["argv"]
      raise ConfigurationError, "CHECK_ARGV_INVALID: #{id}" unless argv.is_a?(Array) && !argv.empty? && argv.all? { |item| item.is_a?(String) && !item.empty? }
      raise ConfigurationError, "CHECK_ARGV_SECRET_FORBIDDEN: #{id}" if argv.any? { |item| VerificationEvidence.secret_bearing?(item) }
      inputs = definition["inputs"]
      unless inputs.is_a?(Array) && !inputs.empty? && inputs.all? { |entry| entry.is_a?(Hash) && entry.keys.sort == %w[path role] }
        raise ConfigurationError, "CHECK_INPUTS_INVALID: #{id}"
      end
      duplicate_input = inputs.group_by { |entry| entry.fetch("path") }.find { |_path, entries| entries.length > 1 }&.first
      raise ConfigurationError, "CHECK_INPUT_DUPLICATE: id=#{id} path=#{duplicate_input}" if duplicate_input
      raise ConfigurationError, "CHECK_TIMEOUT_INVALID: #{id}" unless definition["timeout_seconds"].is_a?(Numeric) && definition["timeout_seconds"].positive?
      raise ConfigurationError, "CHECK_OUTPUT_CONTRACT_INVALID: #{id}" unless %w[process json-status-v1].include?(definition["output_contract"])
    end
  end

  def run_trace_004(root:, io: $stdout, err: $stderr)
    TRACE_004_CHILD_ARGVS.each_with_index do |argv, index|
      io.puts("trace_004_child=#{index + 1} argv=#{argv.join(' ')} status=START")
      stdout_text, stderr_text, process_status = Open3.capture3(*argv, chdir: root)
      io.write(stdout_text)
      err.write(stderr_text)
      unless process_status.success?
        blocked = process_status.exitstatus == 75
        err.puts("trace_004_child=#{index + 1} status=#{blocked ? 'BLOCKED' : 'FAIL'} exit=#{process_status.exitstatus}")
        return blocked ? 75 : 1
      end
      io.puts("trace_004_child=#{index + 1} status=PASS exit=0")
    rescue Errno::ENOENT, Errno::EACCES => error
      err.puts("trace_004_child=#{index + 1} status=BLOCKED diagnostic=#{error.class}")
      return 75
    end
    0
  end

  def load_portable_json(root, relative, diagnostic, max_bytes:)
    errors = []
    snapshot = VerificationEvidence.verified_local_file(
      root,
      relative,
      errors,
      diagnostic,
      max_bytes: max_bytes
    )
    raise ConfigurationError, errors.join(";") unless snapshot

    [snapshot, JSON.parse(snapshot.bytes)]
  rescue JSON::ParserError
    raise ConfigurationError, "#{diagnostic}_JSON_INVALID"
  end

  def portable_runtime_facts(runtime, runtime_snapshot:)
    run = runtime.fetch("run")
    scenario = run.fetch("scenario")
    artifacts = run.fetch("artifacts")
    identity = run.fetch("runtime")
    {
      "path" => LOCAL_CHROME_RUNTIME,
      "sha256" => VerificationEvidence.snapshot_sha256(runtime_snapshot),
      "subject_manifest_path" => scenario.dig("subject", "manifestPath"),
      "subject_manifest_digest" => scenario.dig("subject", "manifestDigest"),
      "tested_subject_digest" => scenario.dig("subject", "testedSubjectDigest"),
      "brand" => identity.fetch("brand"),
      "full_version" => identity.fetch("fullVersion"),
      "major" => identity.fetch("major"),
      "executable_path" => identity.fetch("canonicalPath"),
      "viewport" => identity.fetch("viewport"),
      "launch_succeeded" => identity.dig("launch", "succeeded"),
      "scenario_id" => scenario.dig("contract", "scenarioId"),
      "visual_rule_id" => scenario.dig("contract", "visualRuleId"),
      "response_status" => scenario.dig("response", "status"),
      "response_body_sha256" => scenario.dig("response", "bodySha256"),
      "marker_name" => scenario.dig("response", "marker", "name"),
      "marker_value" => scenario.dig("response", "marker", "value"),
      "screenshot_sha256" => artifacts.dig("screenshot", "sha256"),
      "dom_sha256" => artifacts.dig("dom", "sha256"),
      "transcript_sha256" => artifacts.dig("transcript", "sha256"),
      "console_sha256" => artifacts.dig("console", "sha256")
    }
  rescue KeyError, NoMethodError, TypeError
    raise ConfigurationError, "PORTABLE_RUNTIME_STRUCTURE_INVALID"
  end

  def validate_portable_obligation_manifest(root:, manifest:, subject:, binding:, runtime:, runtime_snapshot:)
    errors = []
    VerificationEvidence.exact_hash(
      manifest,
      VerificationEvidence::OBLIGATION_MANIFEST_FIELDS,
      errors,
      "OBLIGATION_MANIFEST"
    )
    return errors unless manifest.is_a?(Hash)

    errors << "OBLIGATION_MANIFEST_SCHEMA_UNSUPPORTED" unless manifest["schema_version"] == VerificationEvidence::OBLIGATION_MANIFEST_SCHEMA
    errors << "OBLIGATION_MANIFEST_PHASE_MISMATCH" unless manifest["phase"] == VerificationEvidence::PHASE
    errors << "OBLIGATION_MANIFEST_OWNER_MISMATCH" unless manifest["owner"] == VerificationEvidence::OWNER
    errors << "OBLIGATION_MANIFEST_SUBJECT_PATH_MISMATCH" unless manifest["subject_manifest_path"] == binding.fetch("path")
    errors << "OBLIGATION_MANIFEST_SUBJECT_MANIFEST_DIGEST_MISMATCH" unless manifest["subject_manifest_digest"] == binding.fetch("subject_manifest_digest")
    errors << "OBLIGATION_MANIFEST_TESTED_SUBJECT_DIGEST_MISMATCH" unless manifest["tested_subject_digest"] == binding.fetch("tested_subject_digest")

    expected_runtime = runtime_snapshot && {
      "path" => LOCAL_CHROME_RUNTIME,
      "sha256" => VerificationEvidence.snapshot_sha256(runtime_snapshot),
      "media_type" => "application/json",
      "size" => runtime_snapshot.bytes.bytesize
    }
    VerificationEvidence.exact_hash(
      manifest["runtime_artifact"],
      VerificationEvidence::ARTIFACT_FIELDS,
      errors,
      "OBLIGATION_MANIFEST_RUNTIME"
    )
    errors << "OBLIGATION_MANIFEST_RUNTIME_MISMATCH" unless manifest["runtime_artifact"] == expected_runtime

    expected_ci = VerificationEvidence::CI_LOCATOR_PATHS.map do |relative|
      snapshot = VerificationEvidence.verified_local_file(
        root,
        relative,
        errors,
        "OBLIGATION_CI_LOCATOR",
        max_bytes: PORTABLE_JSON_MAX_BYTES
      )
      { "path" => relative, "sha256" => snapshot && VerificationEvidence.snapshot_sha256(snapshot) }
    end
    ci_locators = manifest["ci_locators"]
    unless ci_locators.is_a?(Array)
      errors << "OBLIGATION_MANIFEST_CI_LOCATORS_INVALID"
    else
      ci_locators.each do |entry|
        VerificationEvidence.exact_hash(entry, %w[path sha256], errors, "OBLIGATION_MANIFEST_CI_LOCATOR")
      end
      errors << "OBLIGATION_MANIFEST_CI_LOCATORS_MISMATCH" unless ci_locators == expected_ci
    end

    definitions = VerificationEvidence.obligation_registry
    expected_ids = definitions.map { |definition| definition.fetch("obligation_id") }
    entries = manifest["entries"]
    unless entries.is_a?(Array)
      errors << "OBLIGATION_MANIFEST_ENTRIES_INVALID"
      return errors.uniq
    end
    actual_ids = entries.filter_map { |entry| entry["obligation_id"] if entry.is_a?(Hash) }
    errors << "OBLIGATION_MANIFEST_ENTRY_SET_INVALID" unless actual_ids == expected_ids
    errors << "OBLIGATION_MANIFEST_ENTRY_DUPLICATE" unless actual_ids.uniq.length == actual_ids.length

    matrix_rows = VerificationEvidence.parse_test_matrix(
      root,
      "#{PHASE_DIR}/TEST-MATRIX.md",
      max_bytes: PORTABLE_JSON_MAX_BYTES
    )
    catalog_rows = VerificationEvidence.parse_obligation_catalog(
      root,
      ".planning/PRD-OBLIGATIONS.md",
      max_bytes: PORTABLE_JSON_MAX_BYTES
    )
    runtime_facts = portable_runtime_facts(runtime, runtime_snapshot: runtime_snapshot)
    contracts = check_contracts
    entries.each_with_index do |entry, index|
      VerificationEvidence.exact_hash(
        entry,
        VerificationEvidence::OBLIGATION_MANIFEST_ENTRY_FIELDS,
        errors,
        "OBLIGATION_MANIFEST_ENTRY"
      )
      next unless entry.is_a?(Hash) && index < definitions.length

      definition = definitions.fetch(index)
      obligation_id = definition.fetch("obligation_id")
      expected_path = File.join(EVIDENCE_DIR, "#{obligation_id}.json")
      errors << "OBLIGATION_MANIFEST_ENTRY_PATH_MISMATCH" unless entry["path"] == expected_path
      summary_snapshot = VerificationEvidence.verified_local_file(
        root,
        entry["path"],
        errors,
        "OBLIGATION_SUMMARY",
        max_bytes: PORTABLE_JSON_MAX_BYTES
      )
      next unless summary_snapshot

      errors << "OBLIGATION_MANIFEST_ENTRY_CHECKSUM_MISMATCH" unless entry["sha256"] == VerificationEvidence.snapshot_sha256(summary_snapshot)
      begin
        summary = JSON.parse(summary_snapshot.bytes)
        errors.concat(
          VerificationEvidence.validate_obligation_summary(
            root: root,
            summary: summary,
            definition: definition,
            subject: subject,
            matrix_rows: matrix_rows,
            catalog_rows: catalog_rows,
            runtime_facts: runtime_facts,
            check_contracts: contracts,
            subject_path: binding.fetch("path")
          )
        )
        %w[obligation_id status case_id behavior_id catalog_test evidence_path].each do |field|
          errors << "OBLIGATION_MANIFEST_ENTRY_SUMMARY_MISMATCH: #{field}" unless entry[field] == summary[field]
        end
      rescue JSON::ParserError
        errors << "OBLIGATION_SUMMARY_JSON_INVALID"
      end
    end
    errors.uniq
  rescue ArgumentError, ConfigurationError, KeyError, NoMethodError, TypeError => error
    (errors << error.message).uniq
  end

  def validate_portable_chrome_artifact(root:, io: $stdout, err: $stderr, validator_root: root)
    required = [
      LOCAL_CHROME_RUNTIME,
      DURABLE_SUBJECT,
      OBLIGATION_EVIDENCE_MANIFEST,
      "web/verification/browser-scenarios.json",
      "web/verification/local-chrome-runtime.schema.json",
      "web/scripts/validate-local-chrome-evidence.mjs"
    ]
    missing = required.reject { |relative| File.file?(File.join(root, relative)) }
    unless missing.empty?
      err.puts("portable_chrome_artifact=BLOCKED missing=#{missing.join(',')}")
      return 75
    end
    binding = durable_subject_binding(root)
    subject = binding.fetch("subject")
    runtime_snapshot, runtime = load_portable_json(
      root,
      LOCAL_CHROME_RUNTIME,
      "PORTABLE_RUNTIME",
      max_bytes: PORTABLE_RUNTIME_MAX_BYTES
    )
    _manifest_path, manifest = load_portable_json(
      root,
      OBLIGATION_EVIDENCE_MANIFEST,
      "PORTABLE_OBLIGATION_MANIFEST",
      max_bytes: PORTABLE_JSON_MAX_BYTES
    )
    scenario_snapshot, _scenario = load_portable_json(
      root,
      "web/verification/browser-scenarios.json",
      "PORTABLE_SCENARIO",
      max_bytes: PORTABLE_JSON_MAX_BYTES
    )
    schema_snapshot, _schema = load_portable_json(
      root,
      "web/verification/local-chrome-runtime.schema.json",
      "PORTABLE_RUNTIME_SCHEMA",
      max_bytes: PORTABLE_JSON_MAX_BYTES
    )
    source = <<~'JAVASCRIPT'
      import { readFileSync } from 'node:fs';
      import { pathToFileURL } from 'node:url';
      const [validatorPath] = process.argv.slice(1);
      process.argv[1] = 'phase01-portable-validator';
      const { validateLocalChromeEvidence } = await import(pathToFileURL(validatorPath).href);
      const { evidence, scenario, schema, expectedSubject } = JSON.parse(readFileSync(0, 'utf8'));
      const errors = await validateLocalChromeEvidence(evidence, scenario, schema, {
        expectedSubject,
      });
      if (errors.length > 0) {
        console.error(`portable_runtime_validation=BLOCKED errors=${errors.join(';')}`);
        process.exitCode = 1;
      } else {
        console.log('portable_runtime_validation=PASS live_browser_launched=false');
      }
    JAVASCRIPT
    argv = [
      "/usr/bin/env", "node", "--input-type=module", "--eval", source,
      File.join(validator_root, "web/scripts/validate-local-chrome-evidence.mjs")
    ]
    expected_subject_json = JSON.generate(
      "manifestPath" => binding.fetch("path"),
      "manifestDigest" => binding.fetch("subject_manifest_digest"),
      "testedSubjectDigest" => binding.fetch("tested_subject_digest")
    )
    validator_input_parts = [
      '{"evidence":', runtime_snapshot.bytes,
      ',"scenario":', scenario_snapshot.bytes,
      ',"schema":', schema_snapshot.bytes,
      ',"expectedSubject":', expected_subject_json, "}"
    ]
    validator_input_size = validator_input_parts.sum(&:bytesize)
    if validator_input_size > PORTABLE_STDIN_MAX_BYTES
      raise ConfigurationError,
            "PORTABLE_STDIN_SIZE_LIMIT_EXCEEDED: size=#{validator_input_size} max=#{PORTABLE_STDIN_MAX_BYTES}"
    end
    validator_input = validator_input_parts.join
    stdout_text, stderr_text, process_status = Open3.capture3(*argv, chdir: root, stdin_data: validator_input)
    io.write(stdout_text)
    err.write(stderr_text)
    unless process_status.success?
      err.puts("portable_chrome_artifact=BLOCKED diagnostic=PORTABLE_RUNTIME_INVALID")
      return 75
    end

    manifest_errors = validate_portable_obligation_manifest(
      root: root,
      manifest: manifest,
      subject: subject,
      binding: binding,
      runtime: runtime,
      runtime_snapshot: runtime_snapshot
    )
    unless manifest_errors.empty?
      err.puts("portable_chrome_artifact=BLOCKED errors=#{VerificationEvidence.redact(manifest_errors.join(';'))}")
      return 75
    end

    io.puts("portable_chrome_artifact=PASS runtime_claim=false live_browser_launched=false")
    0
  rescue ConfigurationError => error
    err.puts("portable_chrome_artifact=BLOCKED diagnostic=#{VerificationEvidence.redact(error.message)}")
    75
  rescue Errno::ENOENT, Errno::EACCES => error
    err.puts("portable_chrome_artifact=BLOCKED diagnostic=#{error.class}")
    75
  end

  def durable_subject_binding(root)
    relative = DURABLE_SUBJECT
    errors = []
    snapshot = VerificationEvidence.verified_local_file(
      root,
      relative,
      errors,
      "LOCAL_CHROME_SUBJECT",
      max_bytes: PORTABLE_JSON_MAX_BYTES
    )
    unless snapshot
      diagnostic = errors.empty? ? "LOCAL_CHROME_SUBJECT_MISSING" : errors.join(";")
      raise ConfigurationError, diagnostic
    end

    subject = JSON.parse(snapshot.bytes)
    errors = VerificationEvidence.validate_subject_manifest(
      root: root,
      manifest: subject,
      registries: subject_registries,
      max_bytes: PORTABLE_JSON_MAX_BYTES
    )
    raise ConfigurationError, "LOCAL_CHROME_SUBJECT_INVALID: #{errors.join(';')}" unless errors.empty?

    {
      "path" => relative,
      "subject_manifest_digest" => VerificationEvidence.subject_manifest_digest(subject),
      "tested_subject_digest" => VerificationEvidence.tested_subject_digest(subject.fetch("inputs")),
      "subject" => subject
    }
  rescue JSON::ParserError
    raise ConfigurationError, "LOCAL_CHROME_SUBJECT_JSON_INVALID"
  end

  def validate_local_chrome_runtime(root:, io: $stdout, err: $stderr)
    binding = durable_subject_binding(root)
    producer_argv = [
      "/usr/bin/env", "node", "web/scripts/run-local-chrome-smoke.mjs",
      "--output", LOCAL_CHROME_RUNTIME,
      "--subject-manifest", binding.fetch("path"),
      "--subject-manifest-digest", binding.fetch("subject_manifest_digest"),
      "--tested-subject-digest", binding.fetch("tested_subject_digest")
    ]
    producer_stdout, producer_stderr, producer_status = Open3.capture3(*producer_argv, chdir: root)
    io.write(producer_stdout)
    err.write(producer_stderr)
    return 1 unless producer_status.success?

    validator_argv = [
      "/usr/bin/env", "node", "web/scripts/validate-local-chrome-evidence.mjs",
      "--runtime", LOCAL_CHROME_RUNTIME,
      "--scenario", "web/verification/browser-scenarios.json",
      "--subject-manifest", binding.fetch("path"),
      "--subject-manifest-digest", binding.fetch("subject_manifest_digest"),
      "--tested-subject-digest", binding.fetch("tested_subject_digest")
    ]
    stdout_text, stderr_text, process_status = Open3.capture3(*validator_argv, chdir: root)
    io.write(stdout_text)
    err.write(stderr_text)
    process_status.success? ? 0 : 1
  rescue ConfigurationError => error
    err.puts("local_chrome_runtime=BLOCKED diagnostic=#{VerificationEvidence.redact(error.message)}")
    75
  rescue Errno::ENOENT, Errno::EACCES => error
    err.puts("local_chrome_runtime=BLOCKED diagnostic=#{error.class}")
    75
  end

  def validate_evidence_dir(root, evidence_dir)
    if evidence_dir.is_a?(String) && (Pathname(evidence_dir).absolute? || Pathname(evidence_dir).each_filename.include?(".."))
      raise ConfigurationError, "EVIDENCE_DIR_OUTSIDE_ROOT"
    end
    raise ConfigurationError, "EVIDENCE_DIR_PATH_INVALID" unless VerificationEvidence.canonical_relative_path?(evidence_dir)

    expanded_root = File.realpath(root)
    expanded = File.expand_path(evidence_dir, expanded_root)
    unless expanded.start_with?("#{expanded_root}#{File::SEPARATOR}")
      raise ConfigurationError, "EVIDENCE_DIR_OUTSIDE_ROOT"
    end
    current = expanded_root
    Pathname(evidence_dir).each_filename do |part|
      current = File.join(current, part)
      next unless File.exist?(current) || File.symlink?(current)
      raise ConfigurationError, "EVIDENCE_DIR_SYMLINK: #{current}" if File.lstat(current).symlink?
      raise ConfigurationError, "EVIDENCE_DIR_COMPONENT_NOT_DIRECTORY: #{current}" unless File.directory?(current)
    end
    expanded
  end

  def run(root:, evidence_dir:, definitions:, io: $stdout)
    validate_definitions(definitions)
    root = File.realpath(root)
    evidence_root = validate_evidence_dir(root, evidence_dir)
    FileUtils.mkdir_p(evidence_root, mode: 0o755)

    run_id = "phase01-#{Time.now.utc.strftime('%Y%m%dT%H%M%S')}-#{SecureRandom.hex(6)}"
    run_relative = File.join(evidence_dir, "runs", run_id)
    run_root = File.join(root, run_relative)
    FileUtils.mkdir_p(run_root, mode: 0o755)

    registries = subject_registries(definitions)
    subject_relative = File.join(run_relative, "tested-inputs.json")
    require_live_entry = definitions.any? { |definition| definition.fetch("id") == "local-chrome-runtime" }
    manifest = VerificationEvidence.build_subject_manifest(
      root: root,
      registries: registries,
      manifest_path: subject_relative,
      require_live_entry: require_live_entry
    )
    manifest_digest = VerificationEvidence.subject_manifest_digest(manifest)
    subject_digest = VerificationEvidence.tested_subject_digest(manifest.fetch("inputs"))

    envelopes = []
    evidence_paths = []
    evidence_sha256s = []
    definitions.each do |definition|
      result = execute_child(root, definition)
      check_id = definition.fetch("id")
      stdout_relative = File.join(run_relative, "#{check_id}.stdout.txt")
      stderr_relative = File.join(run_relative, "#{check_id}.stderr.txt")
      File.write(File.join(root, stdout_relative), result.fetch("stdout"))
      File.write(File.join(root, stderr_relative), result.fetch("stderr"))

      artifacts = [
        VerificationEvidence.artifact_record(root, stdout_relative, "text/plain"),
        VerificationEvidence.artifact_record(root, stderr_relative, "text/plain")
      ]
      envelope = VerificationEvidence.build_envelope(
        run_id: run_id,
        check_id: check_id,
        layer: definition.fetch("layer"),
        obligation_ids: definition.fetch("obligation_ids").sort,
        case_ids: definition.fetch("case_ids").sort,
        argv: definition.fetch("argv"),
        cwd: definition.fetch("cwd"),
        started_at: result.fetch("started_at"),
        completed_at: result.fetch("completed_at"),
        environment: environment_identity,
        status: result.fetch("status"),
        exit_code: result.fetch("exit_code"),
        errors: result.fetch("errors"),
        diagnostics: result.fetch("diagnostics"),
        artifacts: artifacts,
        subject_manifest_path: subject_relative,
        subject_manifest_digest: manifest_digest,
        tested_subject_digest: subject_digest
      )

      validation = VerificationEvidence.validate_envelope(
        root: root,
        envelope: envelope,
        registries: registries,
        subject_manifest_path: subject_relative,
        check_contracts: check_contracts(definitions)
      )
      structural = validation.reject { |error| error == "EVIDENCE_STATUS_FAIL" || error == "EVIDENCE_STATUS_BLOCKED" }
      unless structural.empty?
        envelope["status"] = "FAIL"
        envelope["exit_code"] = 70
        envelope["errors"] = (envelope["errors"] + ["EVIDENCE_INDEPENDENT_VALIDATION_FAILED"]).uniq.sort
        envelope["diagnostics"] = (envelope["diagnostics"] + structural.map { |error| VerificationEvidence.redact(error) }).uniq
      end

      envelope_relative = File.join(run_relative, "#{check_id}.json")
      VerificationEvidence.atomic_write_json(File.join(root, envelope_relative), envelope)
      persisted = JSON.parse(File.read(File.join(root, envelope_relative)))
      persisted_validation = VerificationEvidence.validate_envelope(
        root: root,
        envelope: persisted,
        registries: registries,
        subject_manifest_path: subject_relative,
        check_contracts: check_contracts(definitions)
      )
      persisted_structural = persisted_validation.reject { |error| error == "EVIDENCE_STATUS_FAIL" || error == "EVIDENCE_STATUS_BLOCKED" }
      raise ConfigurationError, "EVIDENCE_PERSISTED_INVALID: #{check_id} #{persisted_structural.join('; ')}" unless persisted_structural.empty?
      envelopes << envelope
      evidence_paths << envelope_relative
      evidence_sha256s << Digest::SHA256.file(File.join(root, envelope_relative)).hexdigest
      io.puts format("%-32s %-7s %s", check_id, envelope.fetch("status"), envelope_relative)
    end

    aggregate = VerificationEvidence.build_aggregate(
      run_id: run_id,
      envelopes: envelopes,
      evidence_paths: evidence_paths,
      evidence_sha256s: evidence_sha256s,
      subject_manifest_path: subject_relative,
      subject_manifest_digest: manifest_digest,
      tested_subject_digest: subject_digest
    )
    aggregate_errors = VerificationEvidence.validate_aggregate(root: root, aggregate: aggregate, envelopes: envelopes)
    unless aggregate_errors.empty?
      aggregate["status"] = "FAIL"
      aggregate["errors"] = (aggregate["errors"] + ["AGGREGATE_INDEPENDENT_VALIDATION_FAILED"] + aggregate_errors).uniq.sort
    end
    aggregate_relative = File.join(run_relative, "aggregate.json")
    VerificationEvidence.atomic_write_json(File.join(root, aggregate_relative), aggregate)
    manifest_record = VerificationEvidence.build_evidence_manifest(
      root: root,
      owner: "engineering-verification-foundation",
      envelopes: envelopes,
      evidence_paths: evidence_paths,
      aggregate_path: aggregate_relative,
      subject_manifest_path: subject_relative,
      subject_manifest_digest: manifest_digest,
      tested_subject_digest: subject_digest
    )
    evidence_manifest_relative = File.join(run_relative, "evidence-manifest.json")
    VerificationEvidence.atomic_write_json(File.join(root, evidence_manifest_relative), manifest_record)
    manifest_validation = VerificationEvidence.validate_evidence_manifest(
      root: root,
      manifest: manifest_record,
      registries: registries,
      check_contracts: check_contracts(definitions),
      required_owner: "engineering-verification-foundation"
    )
    structural_manifest_errors = manifest_validation.reject { |error| error == "EVIDENCE_STATUS_FAIL" || error == "EVIDENCE_STATUS_BLOCKED" }
    raise ConfigurationError, "EVIDENCE_MANIFEST_PERSISTED_INVALID: #{structural_manifest_errors.join('; ')}" unless structural_manifest_errors.empty?
    io.puts "aggregate=#{aggregate.fetch('status')} evidence=#{aggregate_relative} manifest=#{evidence_manifest_relative}"
    aggregate.merge("aggregate_path" => aggregate_relative, "evidence_manifest_path" => evidence_manifest_relative)
  end

  def execute_child(root, definition)
    started = Time.now.utc
    stdout_text = ""
    stderr_text = ""
    status = "BLOCKED"
    exit_code = nil
    error_ids = []

    begin
      Open3.popen3(*definition.fetch("argv"), chdir: File.join(root, definition.fetch("cwd")), pgroup: true) do |stdin, stdout, stderr, wait_thread|
        stdin.close
        stdout_reader = Thread.new { stdout.read(1_048_577) }
        stderr_reader = Thread.new { stderr.read(1_048_577) }
        begin
          Timeout.timeout(definition.fetch("timeout_seconds")) { wait_thread.join }
          process_status = wait_thread.value
          stdout_text = stdout_reader.value
          stderr_text = stderr_reader.value
          if process_status.signaled?
            status = "BLOCKED"
            error_ids << "CHILD_INTERRUPTED"
          elsif process_status.exitstatus == 0
            status = "PASS"
            exit_code = 0
          elsif process_status.exitstatus == 75
            status = "BLOCKED"
            exit_code = 75
            error_ids << "CHILD_BLOCKED"
          else
            status = "FAIL"
            exit_code = process_status.exitstatus
            error_ids << "CHILD_NONZERO"
          end
        rescue Timeout::Error
          status = "BLOCKED"
          error_ids << "CHILD_TIMEOUT"
          begin
            Process.kill("TERM", -wait_thread.pid)
          rescue Errno::ESRCH
            nil
          end
          wait_thread.join(1)
          unless wait_thread.join(0)
            begin
              Process.kill("KILL", -wait_thread.pid)
            rescue Errno::ESRCH
              nil
            end
          end
          wait_thread.join
          stdout_text = stdout_reader.value
          stderr_text = stderr_reader.value
        ensure
          stdout_reader.kill if stdout_reader&.alive?
          stderr_reader.kill if stderr_reader&.alive?
        end
      end
    rescue Errno::ENOENT
      status = "BLOCKED"
      error_ids << "CHILD_EXECUTABLE_MISSING"
      stderr_text = "required executable unavailable"
    rescue Errno::EACCES
      status = "BLOCKED"
      error_ids << "CHILD_EXECUTABLE_NOT_RUNNABLE"
      stderr_text = "required executable is not runnable"
    end

    stdout_text = stdout_text.to_s.byteslice(0, 1_048_576).to_s.dup.force_encoding(Encoding::UTF_8).scrub
    stderr_text = stderr_text.to_s.byteslice(0, 1_048_576).to_s.dup.force_encoding(Encoding::UTF_8).scrub
    stdout_text = VerificationEvidence.redact(stdout_text)
    stderr_text = VerificationEvidence.redact(stderr_text)
    if definition.fetch("output_contract") == "json-status-v1" && status == "PASS"
      begin
        document = JSON.parse(stdout_text)
        expected = { "schema_version" => "phase01-child-result-v1", "check_id" => definition.fetch("id"), "status" => "PASS" }
        unless document == expected
          status = "FAIL"
          exit_code = 65
          error_ids << "CHILD_OUTPUT_MALFORMED"
        end
      rescue JSON::ParserError
        status = "FAIL"
        exit_code = 65
        error_ids << "CHILD_OUTPUT_MALFORMED"
      end
    end

    completed = Time.now.utc
    diagnostics = [stdout_text, stderr_text].reject(&:empty?).map { |value| value.lines.first(20).join.strip }.reject(&:empty?)
    diagnostics = ["child completed without diagnostic output"] if diagnostics.empty?
    {
      "status" => status,
      "exit_code" => exit_code,
      "errors" => error_ids.uniq.sort,
      "diagnostics" => diagnostics,
      "stdout" => stdout_text,
      "stderr" => stderr_text,
      "started_at" => started.iso8601(6),
      "completed_at" => completed.iso8601(6)
    }
  end

  def environment_identity
    {
      "ruby_engine" => defined?(RUBY_ENGINE) ? RUBY_ENGINE : "ruby",
      "ruby_version" => RUBY_VERSION,
      "platform" => RUBY_PLATFORM,
      "architecture" => RUBY_PLATFORM.split("-").first,
      "ci" => ENV.key?("CI")
    }
  end

  def cli(argv, root: File.expand_path("../../..", __dir__), io: $stdout, err: $stderr)
    if argv == ["--internal-trace-004"]
      return run_trace_004(root: root, io: io, err: err)
    end
    if argv == ["--internal-portable-chrome-artifact"]
      return validate_portable_chrome_artifact(root: root, io: io, err: err)
    end
    if argv == ["--internal-local-chrome-runtime"]
      return validate_local_chrome_runtime(root: root, io: io, err: err)
    end

    registry_contract!(root: root)
    scopes = []
    evidence_dir = nil
    index = 0
    while index < argv.length
      token = argv[index]
      case token
      when "--self-test"
        scopes << "self-test"
      when "--all"
        scopes << "all"
      when "--ci"
        scopes << "ci"
      when "--timezone"
        scopes << "timezone"
      when "--evidence-dir"
        index += 1
        raise ConfigurationError, "OPTION_EVIDENCE_DIR_VALUE_REQUIRED" if index >= argv.length
        raise ConfigurationError, "OPTION_EVIDENCE_DIR_DUPLICATE" if evidence_dir
        evidence_dir = argv[index]
      else
        raise ConfigurationError, "OPTION_UNKNOWN: #{token}"
      end
      index += 1
    end
    raise ConfigurationError, "OPTION_SELECTOR_REQUIRED" if scopes.empty?
    raise ConfigurationError, "OPTION_SELECTOR_DUPLICATE: #{scopes.find { |scope| scopes.count(scope) > 1 }}" if scopes.uniq.length != scopes.length
    raise ConfigurationError, "OPTION_SELECTOR_CONFLICT: #{scopes.join(',')}" if scopes.length != 1
    raise ConfigurationError, "OPTION_EVIDENCE_DIR_REQUIRED" unless evidence_dir
    definitions = definitions_for(scopes)
    result = run(root: root, evidence_dir: evidence_dir, definitions: definitions, io: io)
    result.fetch("status") == "PASS" ? 0 : 1
  rescue ConfigurationError => error
    err.puts(error.message)
    2
  end
end

if $PROGRAM_NAME == __FILE__
  exit Phase01RunChecks.cli(ARGV)
end
