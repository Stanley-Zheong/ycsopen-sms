package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.service.tenant.*;
import com.ycsopen.sms.core.web.dto.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MySQL persistence and tenant-bound credential proof for Phase 09. */
@SpringBootTest(classes=Phase09TenantCredentialMySqlTest.Application.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("phase01-integration")
@EnabledIfSystemProperty(named="phase01.integration.enabled",matches="true")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class Phase09TenantCredentialMySqlTest {
 private static Phase03ServiceHarness.ServiceSession mysql;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){mysql=Phase03ServiceHarness.startMySql();r.add("spring.datasource.url",()->"jdbc:mysql://"+mysql.host()+":"+mysql.port()+"/phase01?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC");r.add("spring.datasource.username",mysql::username);r.add("spring.datasource.password",mysql::password);r.add("spring.flyway.user",mysql::username);r.add("spring.flyway.password",mysql::password);r.add("spring.jpa.hibernate.ddl-auto",()->"none");}
 @AfterAll static void stop(){if(mysql!=null)mysql.close();}
 @Autowired JdbcTemplate jdbc; @Autowired TenantApiKeyService apiKeys; @Autowired TenantProtocolCredentialService cmpp; @Autowired TestEvents events;
 @BeforeEach void seed(){
  // Keep the fixture tenant-scoped and deterministic. Credential rows must be
  // removed before the actor rows and tenant are recreated for each test.
  jdbc.update("DELETE FROM tenant_api_keys WHERE tenant_id=?",9001L);
  jdbc.update("DELETE FROM tenant_protocol_credentials WHERE tenant_id=?",9001L);
  jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username IN ('phase09-admin','phase09-dev'))");
  jdbc.update("DELETE FROM users WHERE username IN ('phase09-admin','phase09-dev')");
  events.events.clear();
  jdbc.update("DELETE FROM tenants WHERE id=?",9001L);
  jdbc.update("INSERT INTO tenants(id,tenant_no,short_name,full_name,unified_social_credit_code,verification_status,lifecycle_status,inspection_status) VALUES (?,?,?,?,?,'VERIFIED','TRIAL','COMPLETED')",
          9001L,"P09-9001","P09测试租户","P09测试租户有限公司","91350211M000100Y90");
  jdbc.update("INSERT INTO users(username,password_hash,user_type,tenant_id,status) VALUES ('phase09-admin','hash','TENANT_ADMIN',9001,'ACTIVE'),('phase09-dev','hash','TENANT_DEV',9001,'ACTIVE')");
 }
 @Test void realMysqlPreservesTenantScopedApiAndCmppMetadata(){long dev=lookup("phase09-dev");var api=apiKeys.create(dev,new TenantApiKeyCreateRequest("integration-key","synthetic",LocalDateTime.now().plusDays(1),"127.0.0.1/32",10,100,1000,10000));assertThat(api.secret()).isNotBlank();assertThat(jdbc.queryForObject("SELECT app_secret_encrypted FROM tenant_api_keys WHERE id=?",byte[].class,api.id())).isNotEqualTo(api.secret().getBytes(StandardCharsets.UTF_8));assertThat(apiKeys.list(dev)).singleElement().satisfies(row->assertThat(row.secret()).isEqualTo("******"));apiKeys.revoke(dev,api.id());assertThat(jdbc.queryForObject("SELECT status FROM tenant_api_keys WHERE id=?",String.class,api.id())).isEqualTo("DISABLED");assertThat(events.events).anySatisfy(e->assertThat(e.credentialType()).isEqualTo("HTTP_API_KEY"));var c=cmpp.create(dev,new TenantProtocolCredentialCreateRequest("SPID-09","127.0.0.1",7890,"cmp-account","Passw0rd!","127.0.0.1/32",4,100,8));assertThat(cmpp.list(dev)).singleElement().satisfies(row->{assertThat(row.account()).isEqualTo("******");assertThat(row.password()).isEqualTo("******");assertThat(row.endpointPort()).isEqualTo(7890);});cmpp.revoke(dev,c.id());assertThat(jdbc.queryForObject("SELECT status FROM tenant_protocol_credentials WHERE id=?",String.class,c.id())).isEqualTo("DISABLED");}
 @Test void phase09MigrationNamespacesAreDistinct(){assertThat("V1800__tenant_access_api_key_metadata.sql").startsWith("V1800");assertThat("V1801__tenant_access_cmpp_metadata.sql").startsWith("V1801");}
 private long lookup(String username){return jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,username);}
 @SpringBootConfiguration @EnableAutoConfiguration(exclude=RedisAutoConfiguration.class) @Import({TenantApiKeyService.class,TenantProtocolCredentialService.class,OperationAuditService.class,Dependencies.class}) static class Application{}
 @TestConfiguration(proxyBeanMethods=false) static class Dependencies{
  @Bean TenantCredentialSecretProtectionService protection(){
   TenantCredentialSecretProtectionService service=mock(TenantCredentialSecretProtectionService.class);
   when(service.protect(anyLong(),anyLong(),anyString(),any(char[].class))).thenAnswer(invocation -> {
    long tenant=invocation.getArgument(0); long id=invocation.getArgument(1); String field=invocation.getArgument(2);
    char[] value=invocation.getArgument(3); byte[] e=("YENC:"+field+":"+tenant+":"+id).getBytes(StandardCharsets.US_ASCII);
    java.util.Arrays.fill(value,'\0'); return e;
   }); return service;
  }
  @Bean TestEvents events(){return new TestEvents();}
  
 }
 static class TestEvents implements org.springframework.context.ApplicationListener<org.springframework.context.ApplicationEvent>{
  final CopyOnWriteArrayList<TenantCredentialRevokedEvent> events=new CopyOnWriteArrayList<>();
  @Override public void onApplicationEvent(org.springframework.context.ApplicationEvent event){
   Object payload=event instanceof org.springframework.context.PayloadApplicationEvent<?> wrapped
           ? wrapped.getPayload() : event;
   if(payload instanceof TenantCredentialRevokedEvent revoked){events.add(revoked);}
  }
 }
}
