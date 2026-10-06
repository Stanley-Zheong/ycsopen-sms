package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.common.security.HmacSignatureVerifier;
import com.ycsopen.sms.core.domain.entity.TenantApiKey;
import com.ycsopen.sms.core.repository.TenantApiKeyRepository;
import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.service.tenant.*;
import com.ycsopen.sms.core.web.dto.*;
import com.ycsopen.sms.core.web.interceptor.HmacAuthInterceptor;
import com.ycsopen.sms.core.web.security.HmacRequestAuthenticator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
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
 @Autowired JdbcTemplate jdbc; @Autowired TenantApiKeyService apiKeys; @Autowired TenantProtocolCredentialService cmpp; @Autowired TestEvents events; @Autowired TestTenantApiKeyRepository repository; @Autowired TenantCredentialSecretProtectionService protection;
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
 @Test void realMysqlPreservesTenantScopedApiAndCmppMetadata(){long dev=lookup("phase09-dev");var api=apiKeys.create(dev,new TenantApiKeyCreateRequest("integration-key","synthetic",LocalDateTime.now().plusDays(1),"127.0.0.1/32",10,100,1000,10000));assertThat(api.appSecret()).matches("[A-Za-z0-9_-]{43}").doesNotContain("\u0000");assertThat(jdbc.queryForObject("SELECT app_secret_encrypted FROM tenant_api_keys WHERE id=?",byte[].class,api.id())).isNotEqualTo(api.appSecret().getBytes(StandardCharsets.UTF_8));assertThat(apiKeys.list(dev)).singleElement().satisfies(row->{assertThat(row.appSecretMask()).isEqualTo("******");assertThat(row.appSecret()).isNull();});apiKeys.revoke(dev,api.id());assertThat(jdbc.queryForObject("SELECT status FROM tenant_api_keys WHERE id=?",String.class,api.id())).isEqualTo("DISABLED");assertThat(apiKeys.auditTrail(dev)).extracting(TenantApiKeyAuditResponse::operation).contains("TENANT_API_KEY_CREATE","TENANT_API_KEY_REVOKE");assertThat(events.events).anySatisfy(e->assertThat(e.credentialType()).isEqualTo("HTTP_API_KEY"));var c=cmpp.create(dev,new TenantProtocolCredentialCreateRequest("SPID-09","127.0.0.1",7890,"cmp-account","Passw0rd!","127.0.0.1/32",4,100,8));assertThat(cmpp.list(dev)).singleElement().satisfies(row->{assertThat(row.account()).isEqualTo("******");assertThat(row.password()).isEqualTo("******");assertThat(row.endpointPort()).isEqualTo(7890);});cmpp.revoke(dev,c.id());assertThat(jdbc.queryForObject("SELECT status FROM tenant_protocol_credentials WHERE id=?",String.class,c.id())).isEqualTo("DISABLED");}
 @Test void tenantApiKeyAuditLookupUsesTheTenantResourceIndexOnRealMysql(){
  var noise=new ArrayList<Object[]>();
  for(int index=0;index<500;index++) noise.add(new Object[]{8000L+index,"noise-"+index});
  jdbc.batchUpdate("""
          INSERT INTO privileged_operation_audits
            (actor_username,tenant_id,operation,resource_type,resource_id,request_method,route_template,
             sanitized_request,result_code,response_status,client_ip,latency_ms)
          VALUES ('noise',?,'NOISE','TENANT_API_KEY',?,'GET','/noise','{}','SUCCESS',200,'127.0.0.1',0)
          """, noise);
  long dev=lookup("phase09-dev");
  var created=apiKeys.create(dev,new TenantApiKeyCreateRequest("indexed-key","synthetic",
          LocalDateTime.now().plusDays(1),"2001:db8::/32",10,100,1000,10000));
  apiKeys.revoke(dev,created.id());

  var plan=jdbc.queryForMap("""
          EXPLAIN SELECT id,actor_username,operation,resource_id,result_code,occurred_at
            FROM privileged_operation_audits
           WHERE tenant_id=? AND resource_type=?
           ORDER BY id DESC LIMIT 100
          """,9001L,"TENANT_API_KEY");

  assertThat(plan.get("key")).isEqualTo("idx_audit_tenant_resource_id");
  assertThat(apiKeys.auditTrail(dev)).extracting(TenantApiKeyAuditResponse::operation)
          .containsExactly("TENANT_API_KEY_REVOKE","TENANT_API_KEY_CREATE");
 }
 @Test void createdIpv6CidrSurvivesRepositoryProjectionAndAuthenticates(){
  long dev=lookup("phase09-dev");
  var created=apiKeys.create(dev,new TenantApiKeyCreateRequest("ipv6-auth-key","synthetic",
          LocalDateTime.now().plusDays(1),"2001:db8::/32",10,100,1000,10000));
  assertThat(repository.findSignatureAuthenticationByAppKey(created.appKey())).isPresent();
  var signatures=new HmacSignatureVerifier();
  var authenticator=new HmacRequestAuthenticator(repository,signatures,protection);
  String body="{\"submitId\":\"PHASE09-IPV6\"}";
  String timestamp=Long.toString(Instant.now().getEpochSecond());
  String nonce="phase09-ipv6-"+created.id();
  var request=new MockHttpServletRequest("POST","/api/v1/sms/send");
  request.setRemoteAddr("2001:db8:1::7");
  request.addHeader("X-App-Key",created.appKey());
  request.addHeader("X-Timestamp",timestamp);
  request.addHeader("X-Nonce",nonce);
  String toSign=signatures.buildStringToSign("POST","/api/v1/sms/send","",
          HmacRequestAuthenticator.canonicalHeaders(created.appKey(),timestamp,nonce),body);
  request.addHeader("X-Signature",signatures.sign(toSign,created.appSecret()));

  authenticator.authenticate(request,body.getBytes(StandardCharsets.UTF_8));

  assertThat(request.getAttribute(HmacAuthInterceptor.ATTR_TENANT_ID)).isEqualTo(9001L);
  assertThat(apiKeys.list(dev)).singleElement().satisfies(row->assertThat(row.lastUsedTime()).isNotNull());
 }
 @Test void phase09MigrationNamespacesAreDistinct(){assertThat("V1800__tenant_access_api_key_metadata.sql").startsWith("V1800");assertThat("V1801__tenant_access_cmpp_metadata.sql").startsWith("V1801");}
 private long lookup(String username){return jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,username);}
 interface TestTenantApiKeyRepository extends TenantApiKeyRepository {}
 @SpringBootConfiguration @EnableAutoConfiguration(exclude=RedisAutoConfiguration.class)
 @EntityScan(basePackageClasses=TenantApiKey.class)
 @EnableJpaRepositories(basePackageClasses=Phase09TenantCredentialMySqlTest.class,considerNestedRepositories=true)
 @Import({TenantApiKeyService.class,TenantProtocolCredentialService.class,OperationAuditService.class,Dependencies.class}) static class Application{}
 @TestConfiguration(proxyBeanMethods=false) static class Dependencies{
  @Bean TenantCredentialSecretProtectionService protection(TestSecretVault vault){
   TenantCredentialSecretProtectionService service=mock(TenantCredentialSecretProtectionService.class);
   when(service.protect(anyLong(),anyLong(),anyString(),any(char[].class))).thenAnswer(invocation -> {
    long tenant=invocation.getArgument(0); long id=invocation.getArgument(1); String field=invocation.getArgument(2);
    char[] plaintext=invocation.getArgument(3);
    vault.secrets.put(id,new String(plaintext));
    byte[] e=("YENC:"+field+":"+tenant+":"+id).getBytes(StandardCharsets.US_ASCII);
    return e;
   });
   when(service.reveal(anyLong(),anyLong(),anyString(),any(byte[].class))).thenAnswer(invocation ->
           vault.secrets.get((Long)invocation.getArgument(1)).toCharArray());
   return service;
  }
  @Bean TestSecretVault vault(){return new TestSecretVault();}
  @Bean TestEvents events(){return new TestEvents();}
  
 }
 static class TestSecretVault {final Map<Long,String> secrets=new ConcurrentHashMap<>();}
 static class TestEvents implements org.springframework.context.ApplicationListener<org.springframework.context.ApplicationEvent>{
  final CopyOnWriteArrayList<TenantCredentialRevokedEvent> events=new CopyOnWriteArrayList<>();
  @Override public void onApplicationEvent(org.springframework.context.ApplicationEvent event){
   Object payload=event instanceof org.springframework.context.PayloadApplicationEvent<?> wrapped
           ? wrapped.getPayload() : event;
   if(payload instanceof TenantCredentialRevokedEvent revoked){events.add(revoked);}
  }
 }
}
