package com.ycsopen.sms.core.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.sql.DriverManager;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real MySQL, S3-compatible MinIO, and SoftHSM boundary preflight. */
@EnabledIfSystemProperty(named = "phase03.integration.enabled", matches = "true")
class Phase03FixturePreflightTest {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void executesAllThreePinnedServicesAndCleansEveryOwnedResource() throws Exception {
        try (Phase03ServiceHarness.FixtureSet fixtures = Phase03ServiceHarness.startAll()) {
            assertMySql(fixtures.mysql());
            assertMinio(fixtures.minio());
            assertSoftHsm(fixtures.softHsm());
        }
    }

    private static void assertMySql(Phase03ServiceHarness.ServiceSession mysql) throws Exception {
        String jdbcUrl = "jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/phase01"
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        try (var connection = DriverManager.getConnection(jdbcUrl, mysql.username(), mysql.password());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT 1, @@version, DATABASE(), CURRENT_USER()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
            assertThat(result.getString(2)).startsWith("8.4.11");
            assertThat(result.getString(3)).isEqualTo("phase01");
            assertThat(result.getString(4)).startsWith(mysql.username() + "@");
        }
    }

    private static void assertMinio(Phase03ServiceHarness.ServiceSession minio) {
        URI endpoint = URI.create("http://" + minio.host() + ":" + minio.port());
        String bucket = "phase03-java-" + randomHex(6);
        String key = "synthetic/preflight";
        byte[] payload = "phase03-java-s3-boundary".getBytes(StandardCharsets.UTF_8);
        S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        try (S3Client client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .serviceConfiguration(pathStyle)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.username(), minio.password())))
                .httpClient(UrlConnectionHttpClient.create())
                .build()) {
            client.createBucket(request -> request.bucket(bucket));
            try {
                client.putObject(request -> request.bucket(bucket).key(key), RequestBody.fromBytes(payload));
                assertThat(client.getObjectAsBytes(request -> request.bucket(bucket).key(key)).asByteArray())
                        .containsExactly(payload);
                try (S3Client anonymous = S3Client.builder()
                        .endpointOverride(endpoint)
                        .region(Region.US_EAST_1)
                        .serviceConfiguration(pathStyle)
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .httpClient(UrlConnectionHttpClient.create())
                        .build()) {
                    assertThatThrownBy(() -> anonymous.getObjectAsBytes(
                            request -> request.bucket(bucket).key(key)))
                            .isInstanceOf(S3Exception.class)
                            .extracting(error -> ((S3Exception) error).statusCode())
                            .isEqualTo(403);
                }
            } finally {
                client.deleteObject(request -> request.bucket(bucket).key(key));
                client.deleteBucket(request -> request.bucket(bucket));
            }
        }
    }

    private static void assertSoftHsm(Phase03ServiceHarness.ServiceSession session) throws Exception {
        Phase03ServiceHarness.SoftHsmHandoff handoff = session.softHsm();
        assertThat(Phase03ServiceHarness.runChecked(
                java.util.List.of(handoff.cli().toString(), "--version"),
                java.util.Map.of("SOFTHSM2_CONF", handoff.config().toString())).stdout().strip())
                .isEqualTo("2.7.0");

        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        String testClasspath = System.getProperty(
                "surefire.test.class.path", System.getProperty("java.class.path"));
        Path destination = handoff.config().getParent().getParent();
        Phase03ServiceHarness.CommandResult probe = Phase03ServiceHarness.runChecked(
                java.util.List.of(
                        javaExecutable.toString(), "-cp", testClasspath,
                        Phase03FixturePreflightTest.class.getName(), "pkcs11-probe", destination.toString()),
                java.util.Map.of("SOFTHSM2_CONF", handoff.config().toString()));
        assertThat(probe.stdout().strip()).isEqualTo("PKCS11_PREFLIGHT_PASS");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[0].equals("pkcs11-probe")) {
            throw new IllegalArgumentException("closed PKCS#11 probe invocation required");
        }
        Phase03ServiceHarness.SoftHsmHandoff handoff = Phase03ServiceHarness.readHandoff(Path.of(args[1]));
        runPkcs11Probe(handoff);
        System.out.println("PKCS11_PREFLIGHT_PASS");
    }

    private static void runPkcs11Probe(Phase03ServiceHarness.SoftHsmHandoff handoff) throws Exception {
        Path providerConfig = Files.createTempFile(handoff.config().getParent(), "sunpkcs11-", ".cfg");
        Files.writeString(providerConfig,
                "name=Phase03Preflight" + randomHex(4) + "\n"
                        + "library=" + handoff.library() + "\n"
                        + "slot=" + handoff.slot() + "\n",
                StandardCharsets.UTF_8);
        Provider provider = Security.getProvider("SunPKCS11").configure(providerConfig.toString());
        Security.addProvider(provider);
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS11", provider);
            keyStore.load(null, handoff.userPin());

            KeyGenerator aesGenerator = KeyGenerator.getInstance("AES", provider);
            aesGenerator.init(256);
            SecretKey aes = aesGenerator.generateKey();
            assertThat(aes.getEncoded()).isNull();
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
            cipher.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal("phase03-token-aes".getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, aes, new GCMParameterSpec(128, nonce));
            assertThat(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8))
                    .isEqualTo("phase03-token-aes");

            KeyGenerator hmacGenerator = KeyGenerator.getInstance("HmacSHA256", provider);
            hmacGenerator.init(256);
            SecretKey hmacKey = hmacGenerator.generateKey();
            assertThat(hmacKey.getEncoded()).isNull();
            Mac mac = Mac.getInstance("HmacSHA256", provider);
            mac.init(hmacKey);
            assertThat(mac.doFinal("phase03-token-hmac".getBytes(StandardCharsets.UTF_8))).hasSize(32);
        } finally {
            Security.removeProvider(provider.getName());
            Files.deleteIfExists(providerConfig);
        }
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }
}
