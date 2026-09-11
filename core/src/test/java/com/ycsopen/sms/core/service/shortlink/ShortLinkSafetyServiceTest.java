package com.ycsopen.sms.core.service.shortlink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortLinkSafetyServiceTest {
    private JdbcTemplate jdbc;
    private ShortLinkSafetyService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase48-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        service = new ShortLinkSafetyService(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createsPendingShortLinkFromApprovedPublicUrlThenHumanApprovalEnablesRedirectAndAnalytics() {
        var link = service.create(new ShortLinkSafetyService.CreateCommand(7L, "https://example.com/campaign?a=1",
                null, LocalDate.now().plusDays(30), List.of("https://example.com/campaign?a=1"),
                List.of("93.184.216.34")), "tenant-user");

        assertThat(link.status()).isEqualTo("PENDING");
        assertThat(link.shortUrl()).contains("https://s.ycsopen.test/s/");
        assertThat(link.validUntil()).isBeforeOrEqualTo(LocalDate.now().plusDays(365));
        assertThat(link.automatedResultJson()).contains("\"verdict\":\"PASS\"").contains("DOMAIN_APPROVED");

        assertThat(service.redirect(link.shortCode(), new ShortLinkSafetyService.ClickCommand("visitor-1", "华东", "MOBILE")).redirect())
                .isFalse();
        var approved = service.approve(link.id(), new ShortLinkSafetyService.ReviewCommand("域名证据和自动审核均通过"), "operator");
        var decision = service.redirect(approved.shortCode(), new ShortLinkSafetyService.ClickCommand("visitor-1", "华东", "MOBILE"));
        var analytics = service.analytics(7L);

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(decision.redirect()).isTrue();
        assertThat(decision.targetUrl()).isEqualTo("https://example.com/campaign?a=1");
        assertThat(analytics.uniqueClicks()).isOne();
        assertThat(analytics.regions()).singleElement().satisfies(row -> {
            assertThat(row.label()).isEqualTo("华东");
            assertThat(row.count()).isOne();
        });
    }

    @Test
    void privateNetworkMaliciousAndUnapprovedDomainsCannotBeApprovedOrRedirected() {
        assertThatThrownBy(() -> service.create(new ShortLinkSafetyService.CreateCommand(7L,
                "http://127.0.0.1/admin", null, LocalDate.now().plusDays(30), List.of(), List.of("127.0.0.1")), "tenant"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内网");

        var blocked = service.create(new ShortLinkSafetyService.CreateCommand(7L,
                "https://blocked.example/phishing", null, LocalDate.now().plusDays(30),
                List.of("https://blocked.example/phishing"), List.of("93.184.216.34")), "tenant");

        assertThat(blocked.status()).isEqualTo("PENDING");
        assertThat(blocked.automatedResultJson()).contains("DOMAIN_BLACKLISTED").contains("MALICIOUS_SIGNAL");
        assertThatThrownBy(() -> service.approve(blocked.id(), new ShortLinkSafetyService.ReviewCommand("误批准"), "operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自动安全审核未通过");
        assertThat(service.redirect(blocked.shortCode(), new ShortLinkSafetyService.ClickCommand("v", "UNKNOWN", "BROWSER")).redirect())
                .isFalse();
    }

    @Test
    void rejectionExpiryAndTargetDriftNeverRedirectAndDriftEmitsOfflineAudit() {
        var rejected = service.create(new ShortLinkSafetyService.CreateCommand(7L, "https://example.com/reject",
                null, LocalDate.now().plusDays(30), List.of(), List.of("93.184.216.34")), "tenant");
        service.reject(rejected.id(), new ShortLinkSafetyService.ReviewCommand("目标不符合投放要求"), "operator");
        assertThat(service.redirect(rejected.shortCode(), new ShortLinkSafetyService.ClickCommand("v", "UNKNOWN", "BROWSER")).redirect())
                .isFalse();

        var approved = service.create(new ShortLinkSafetyService.CreateCommand(7L, "https://example.com/live",
                null, LocalDate.now().plusDays(30), List.of(), List.of("93.184.216.34")), "tenant");
        service.approve(approved.id(), new ShortLinkSafetyService.ReviewCommand("通过"), "operator");
        var offline = service.inspect(approved.id(), new ShortLinkSafetyService.InspectCommand(
                "https://example.com/live", List.of("10.0.0.5")), "system");

        assertThat(offline.status()).isEqualTo("OFFLINE");
        assertThat(service.redirect(offline.shortCode(), new ShortLinkSafetyService.ClickCommand("v", "UNKNOWN", "BROWSER")).redirect())
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM short_link_audits WHERE audit_action='TARGET_OFFLINE_ALERT'",
                Integer.class)).isOne();
    }

    @Test
    void validityCannotExceedThreeHundredSixtyFiveDays() {
        assertThatThrownBy(() -> service.create(new ShortLinkSafetyService.CreateCommand(7L,
                "https://example.com/too-long", null, LocalDate.now().plusDays(366), List.of(), List.of()), "tenant"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("365");
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE short_links(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  target_url VARCHAR(2000) NOT NULL,
                  custom_domain VARCHAR(128),
                  short_code VARCHAR(16) NOT NULL UNIQUE,
                  valid_until DATE NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                  click_count BIGINT NOT NULL DEFAULT 0,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  target_version INT NOT NULL DEFAULT 1,
                  short_url VARCHAR(255),
                  immutable_target_sha256 CHAR(64),
                  automated_result_json VARCHAR(4000),
                  screenshot_evidence_ref VARCHAR(255),
                  domain_evidence_json VARCHAR(4000),
                  risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
                  review_opinion VARCHAR(255),
                  reviewed_by VARCHAR(64),
                  reviewed_at TIMESTAMP,
                  offline_reason VARCHAR(255),
                  offline_at TIMESTAMP,
                  last_recheck_at TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE short_link_audits(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  short_link_id BIGINT NOT NULL,
                  auto_check_result VARCHAR(4000),
                  risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
                  reviewer VARCHAR(64),
                  review_comment VARCHAR(255),
                  reviewed_at TIMESTAMP,
                  last_recheck_at TIMESTAMP,
                  audit_action VARCHAR(32) NOT NULL DEFAULT 'AUTO_REVIEW',
                  actor VARCHAR(64),
                  result_status VARCHAR(32),
                  evidence_json VARCHAR(4000),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE short_link_domains(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  domain_name VARCHAR(128) NOT NULL,
                  domain_kind VARCHAR(16) NOT NULL,
                  filing_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
                  domain_age_days INT NOT NULL DEFAULT 365,
                  status VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
                  evidence_json VARCHAR(4000)
                )
                """);
        jdbc.execute("""
                CREATE TABLE short_link_click_events(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  short_link_id BIGINT NOT NULL,
                  visitor_hash CHAR(64) NOT NULL,
                  region VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
                  device_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
                  clicked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("INSERT INTO short_link_domains(domain_name, domain_kind, filing_status, domain_age_days, status, evidence_json) VALUES ('example.com','TARGET','APPROVED',3650,'APPROVED','{}')");
        jdbc.update("INSERT INTO short_link_domains(domain_name, domain_kind, filing_status, domain_age_days, status, evidence_json) VALUES ('s.ycsopen.test','SHORT','APPROVED',3650,'APPROVED','{}')");
        jdbc.update("INSERT INTO short_link_domains(domain_name, domain_kind, filing_status, domain_age_days, status, evidence_json) VALUES ('blocked.example','TARGET','APPROVED',3650,'BLACKLISTED','{}')");
    }
}
