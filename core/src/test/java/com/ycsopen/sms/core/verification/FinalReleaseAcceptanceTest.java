package com.ycsopen.sms.core.verification;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FinalReleaseAcceptanceTest {

    private static final Set<String> FINAL_RELEASE_OBLIGATIONS = Set.of(
            "OBL-NFR-CHINESE",
            "OBL-NFR-TIMEZONE",
            "OBL-NFR-NO-MOBILE-APP",
            "OBL-DOD-01-FUNCTIONAL",
            "OBL-DOD-02-REAL-CHANNELS",
            "OBL-DOD-03-COMPLIANCE",
            "OBL-DOD-04-BILLING",
            "OBL-DOD-08-LIFECYCLE",
            "OBL-DOD-09-COMPLAINT-RATIO",
            "OBL-ACCEPT-CROSS-PROTOCOL-COMPOSITION",
            "OBL-FINAL-TODO-EMPTY");

    private static final Pattern ACTIVE_TODO = Pattern.compile("^- \\[ ] .+");

    @Test
    void prdCatalogAndFinalReleaseEvidenceAreComplete() throws IOException {
        Path root = repositoryRoot();
        Path catalog = root.resolve(".planning/PRD-OBLIGATIONS.md");
        List<String> obligationLines = Files.readAllLines(catalog, StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith("- OBL-"))
                .toList();

        assertThat(obligationLines).hasSize(522);
        assertThat(obligationLines)
                .extracting(line -> line.split(" \\| ", -1)[0].substring(2))
                .doesNotHaveDuplicates();

        Set<String> requirementIds = obligationLines.stream()
                .map(line -> line.split(" \\| ", -1)[2])
                .flatMap(value -> Stream.of(value.split(",")))
                .filter(value -> value.startsWith("REQ-"))
                .collect(Collectors.toSet());
        assertThat(requirementIds).hasSize(108);

        Map<String, String> finalEvidence = obligationLines.stream()
                .map(line -> line.split(" \\| ", -1))
                .filter(fields -> fields.length == 9)
                .filter(fields -> fields[3].equals("final-release-acceptance"))
                .collect(Collectors.toMap(
                        fields -> fields[0].substring(2),
                        fields -> fields[7]));

        assertThat(finalEvidence.keySet()).containsExactlyInAnyOrderElementsOf(FINAL_RELEASE_OBLIGATIONS);

        Path phaseDir = root.resolve(".planning/phases/56-final-release-acceptance");
        for (Map.Entry<String, String> entry : finalEvidence.entrySet()) {
            Path evidence = phaseDir.resolve(entry.getValue());
            assertThat(evidence).as(entry.getKey() + " evidence file").exists();
            String body = Files.readString(evidence, StandardCharsets.UTF_8);
            assertThat(body).contains("\"obligation\": \"" + entry.getKey() + "\"");
            assertThat(body).contains("\"status\": \"PASS\"");
        }
    }

    @Test
    void repositoryHasNoActiveUncheckedProjectTodos() throws IOException {
        List<String> openTodos = new ArrayList<>();
        for (Path file : scannableProjectFiles(repositoryRoot())) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                if (ACTIVE_TODO.matcher(lines.get(index)).matches()) {
                    openTodos.add(repositoryRoot().relativize(file) + ":" + (index + 1) + ":"
                            + lines.get(index));
                }
            }
        }
        assertThat(openTodos).isEmpty();
    }

    @Test
    void releaseScopeIsChromeOnlyAndContainsNoNativeMobileApp() throws IOException {
        Path root = repositoryRoot();
        assertThat(Files.readString(root.resolve("README.md"), StandardCharsets.UTF_8))
                .contains("桌面版 Google Chrome")
                .contains("不下载额外浏览器");
        assertThat(Files.readString(root.resolve("docs/PRD.md"), StandardCharsets.UTF_8))
                .contains("仅支持本机当前安装的桌面版 Google Chrome")
                .contains("不下载 Chrome for Testing")
                .contains("不执行跨版本或多浏览器矩阵");

        List<Path> mobileDirectories;
        try (Stream<Path> paths = Files.walk(root, 4)) {
            mobileDirectories = paths
                    .filter(Files::isDirectory)
                    .filter(path -> !isIgnoredPath(root, path))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.equals("android") || name.equals("ios") || name.equals("mobile");
                    })
                    .toList();
        }
        assertThat(mobileDirectories).isEmpty();
    }

    @Test
    void finalReleaseCompositionSurfacesExist() {
        Path root = repositoryRoot();
        assertThat(List.of(
                "core/src/main/java/com/ycsopen/sms/core/service/delivery/HttpSmsUpstreamProviderClient.java",
                "core/src/main/java/com/ycsopen/sms/core/cmpp/CmppSmsUpstreamProviderClient.java",
                "core/src/main/java/com/ycsopen/sms/core/cmpp/CmppMessageSubmitAcceptanceAdapter.java",
                "core/src/main/java/com/ycsopen/sms/core/cmpp/CmppDownstreamGatewaySession.java",
                "core/src/main/java/com/ycsopen/sms/core/service/routing/RoutingEngine.java",
                "core/src/main/java/com/ycsopen/sms/core/service/delivery/MessageReceiptErrorOperationsService.java",
                "core/src/main/java/com/ycsopen/sms/core/service/webhook/WebhookDeliveryTransportService.java",
                "core/src/main/java/com/ycsopen/sms/core/service/billing/BillingService.java",
                "core/src/main/java/com/ycsopen/sms/core/service/billing/ReconciliationSettlementService.java",
                "core/src/main/java/com/ycsopen/sms/core/service/unsubscribe/UnsubscribeComplianceService.java",
                "core/src/main/java/com/ycsopen/sms/core/service/risk/BlacklistRiskControlService.java",
                "core/src/main/java/com/ycsopen/sms/core/service/shortlink/ShortLinkSafetyService.java",
                "core/src/main/java/com/ycsopen/sms/core/service/complaint/ComplaintRatioDashboardService.java",
                "core/src/main/java/com/ycsopen/sms/core/web/controller/TenantRegistrationController.java",
                "core/src/main/java/com/ycsopen/sms/core/web/controller/TenantRiskAutoPauseController.java",
                "core/src/main/java/com/ycsopen/sms/core/web/controller/RetentionArchiveController.java",
                "web/src/pages/admin/complaints/AdminComplaintAnalyticsPage.tsx",
                "web/src/pages/admin/channels/RoutingPolicyPage.tsx",
                "web/src/pages/admin/risk/BlacklistRiskControlPage.tsx",
                "web/src/pages/admin/billing/AdminReconciliationSettlementPage.tsx",
                "web/src/pages/tenant/send/SendPage.tsx",
                "web/src/pages/tenant/webhooks/TenantWebhooksPage.tsx"))
                .allSatisfy(relative -> assertThat(root.resolve(relative)).as(relative).exists());
    }

    @Test
    void simplifiedChineseAndTimezoneContractsRemainReleaseVisible() throws IOException {
        Path root = repositoryRoot();
        String readme = Files.readString(root.resolve("README.md"), StandardCharsets.UTF_8);
        String manual = Files.readString(root.resolve("docs/使用手册.md"), StandardCharsets.UTF_8);
        String prd = Files.readString(root.resolve("docs/PRD.md"), StandardCharsets.UTF_8);

        assertThat(readme + manual + prd)
                .contains("简体中文")
                .contains("UTC+8")
                .contains("Asia/Shanghai");
        assertThat(root.resolve("core/src/test/java/com/ycsopen/sms/core/verification/Phase01TimezoneContractTest.java"))
                .exists();
    }

    private static List<Path> scannableProjectFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnoredPath(root, path))
                    .filter(FinalReleaseAcceptanceTest::hasTextExtension)
                    .filter(path -> isProjectPath(root, path))
                    .toList();
        }
    }

    private static boolean isProjectPath(Path root, Path path) {
        Path relative = root.relativize(path);
        if (relative.getNameCount() == 0) {
            return false;
        }
        String first = relative.getName(0).toString();
        return first.equals(".planning")
                || first.equals("docs")
                || first.equals("core")
                || first.equals("web")
                || first.equals("README.md")
                || first.equals("LICENSE.md");
    }

    private static boolean isIgnoredPath(Path root, Path path) {
        String relative = root.relativize(path).toString();
        return relative.equals(".git")
                || relative.startsWith(".git/")
                || relative.contains("/target/")
                || relative.contains("/node_modules/")
                || relative.contains("/dist/")
                || relative.contains("/coverage/");
    }

    private static boolean hasTextExtension(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".md")
                || name.endsWith(".java")
                || name.endsWith(".ts")
                || name.endsWith(".tsx")
                || name.endsWith(".js")
                || name.endsWith(".jsx")
                || name.endsWith(".json")
                || name.endsWith(".yaml")
                || name.endsWith(".yml")
                || name.endsWith(".properties")
                || name.endsWith(".xml")
                || name.endsWith(".sql")
                || name.endsWith(".css")
                || name.endsWith(".html");
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (current.getFileName() != null && current.getFileName().toString().equals("core")) {
            return current.getParent();
        }
        return current;
    }
}
