package com.ycsopen.sms.core.service.delivery;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageStatusQueryServiceTest {
    private JdbcTemplate jdbc;
    private MessageStatusQueryService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase24-status-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE message_submits(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  submit_id VARCHAR(64) NOT NULL,
                  status VARCHAR(16) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_tasks(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  submit_id BIGINT,
                  message_id VARCHAR(64) NOT NULL,
                  send_status VARCHAR(16) NOT NULL,
                  channel_id BIGINT,
                  channel_msg_id VARCHAR(128),
                  error_code VARCHAR(64),
                  error_message VARCHAR(255),
                  send_time TIMESTAMP,
                  deliver_time TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_send_outbox(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  task_id BIGINT NOT NULL,
                  state VARCHAR(16) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE billing_records(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  task_ref_id BIGINT NOT NULL,
                  billing_status VARCHAR(16) NOT NULL
                )
                """);
        service = new MessageStatusQueryService(jdbc);
    }

    @Test
    void statusQueryReturnsTraceWithoutProtectedInput() {
        jdbc.update("INSERT INTO message_submits(id, tenant_id, submit_id, status) VALUES (10,17,'SUBMIT-1','ACCEPTED')");
        jdbc.update("""
                INSERT INTO message_tasks(id, tenant_id, submit_id, message_id, send_status, channel_id, channel_msg_id)
                VALUES (91,17,10,'MSG_1','DELIVERED',42,'UP-1')
                """);
        jdbc.update("INSERT INTO message_send_outbox(task_id, state) VALUES (91,'SENT')");
        jdbc.update("INSERT INTO billing_records(task_ref_id, billing_status) VALUES (91,'CONFIRMED')");

        var detail = service.status(17L, "MSG_1");

        assertThat(detail.submitId()).isEqualTo("SUBMIT-1");
        assertThat(detail.sendStatus()).isEqualTo("DELIVERED");
        assertThat(detail.providerMessageId()).isEqualTo("UP-1");
        assertThat(detail.billingStatus()).isEqualTo("CONFIRMED");
        assertThat(detail.toString()).doesNotContain("13800138000");
    }

    @Test
    void crossTenantStatusQueryDoesNotRevealExistence() {
        jdbc.update("INSERT INTO message_tasks(id, tenant_id, message_id, send_status) VALUES (91,17,'MSG_2','SENT')");

        assertThatThrownBy(() -> service.status(18L, "MSG_2"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("MESSAGE_STATUS_NOT_FOUND");
    }
}
