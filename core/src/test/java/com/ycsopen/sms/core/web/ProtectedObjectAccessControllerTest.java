package com.ycsopen.sms.core.web;

import com.ycsopen.sms.core.common.security.object.PrivateObjectStorePort;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ProtectedObjectAccessControllerTest {

    private static final String TOKEN =
            "ocap_v1_AAAAAAAAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String OBJECT_ID = "pobj_v1_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String TENANT = "tenant:22222222-2222-4222-8222-222222222222";
    private static final String SUBJECT = "subject:reviewer-7";
    private static final String ACCESS_PURPOSE = "registration-review";

    @Test
    void returnsOnlyCompletelyMaterializedAuthenticatedBytesWithPrivateResponseHeaders() throws Exception {
        ProtectedObjectService service = mock(ProtectedObjectService.class);
        byte[] plaintext = "authenticated evidence".getBytes(StandardCharsets.UTF_8);
        when(service.read(any())).thenReturn(new ProtectedObjectService.ProtectedObjectData(
                plaintext, "application/pdf",
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE));
        MockMvc mvc = mvc(service);

        mvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(content().bytes(plaintext))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().doesNotExist("Location"));

        ArgumentCaptor<ProtectedObjectService.ReadRequest> request =
                ArgumentCaptor.forClass(ProtectedObjectService.ReadRequest.class);
        verify(service).read(request.capture());
        assertThat(request.getValue().protectedObjectId()).isEqualTo(OBJECT_ID);
        assertThat(request.getValue().capabilityToken()).isEqualTo(TOKEN);
        assertThat(request.getValue().tenantScope()).isEqualTo(TENANT);
        assertThat(request.getValue().subject()).isEqualTo(SUBJECT);
        assertThat(request.getValue().accessPurpose()).isEqualTo(ACCESS_PURPOSE);
        assertThat(request.getValue().objectPurpose())
                .isEqualTo(PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE);
    }

    @Test
    void denialAndProviderFaultsReturnStableGenericBodiesWithoutRequestOrStorageDetails() throws Exception {
        ProtectedObjectService service = mock(ProtectedObjectService.class);
        when(service.read(any())).thenThrow(ProtectedObjectService.Failure.denied());
        MvcResult denied = mvc(service).perform(validRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROTECTED_OBJECT_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("protected object access denied"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertLeakFree(denied);

        service = mock(ProtectedObjectService.class);
        when(service.read(any())).thenThrow(ProtectedObjectService.Failure.unavailable());
        MvcResult unavailable = mvc(service).perform(validRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROTECTED_OBJECT_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("protected object service is unavailable"))
                .andReturn();
        assertLeakFree(unavailable);
    }

    @Test
    void malformedPurposeIsRejectedBeforeServiceAndNeverEchoesCapability() throws Exception {
        ProtectedObjectService service = mock(ProtectedObjectService.class);
        MvcResult result = mvc(service).perform(get(
                        ProtectedObjectAccessController.BASE_PATH
                                + ProtectedObjectAccessController.CAPABILITY_ROUTE,
                        TOKEN)
                        .header(ProtectedObjectAccessController.TENANT_HEADER, TENANT)
                        .header(ProtectedObjectAccessController.SUBJECT_HEADER, SUBJECT)
                        .header(ProtectedObjectAccessController.OBJECT_ID_HEADER, OBJECT_ID)
                        .header(ProtectedObjectAccessController.ACCESS_PURPOSE_HEADER, ACCESS_PURPOSE)
                        .header(ProtectedObjectAccessController.OBJECT_PURPOSE_HEADER,
                                "https://storage.invalid/private?token=canary"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROTECTED_OBJECT_INPUT_INVALID"))
                .andExpect(jsonPath("$.message").value("protected object input is invalid"))
                .andReturn();

        assertLeakFree(result);
        verify(service, org.mockito.Mockito.never()).read(any());
    }

    @Test
    void controllerSurfaceHasNoDirectPublicOrPresignedObjectOperation() {
        assertThat(Arrays.stream(ProtectedObjectAccessController.class.getDeclaredMethods())
                .map(Method::getName))
                .allMatch(name -> !name.toLowerCase().contains("presign")
                        && !name.toLowerCase().contains("public")
                        && !name.toLowerCase().contains("redirect")
                        && !name.toLowerCase().contains("storageurl"));
        assertThat(ProtectedObjectAccessController.BASE_PATH).startsWith("/api/v1/");
        assertThat(ProtectedObjectAccessController.CAPABILITY_ROUTE)
                .isEqualTo("/capabilities/{capability}");
    }

    private static MockMvc mvc(ProtectedObjectService service) {
        return standaloneSetup(new ProtectedObjectAccessController(service)).build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest() {
        return get(ProtectedObjectAccessController.BASE_PATH
                        + ProtectedObjectAccessController.CAPABILITY_ROUTE, TOKEN)
                .header(ProtectedObjectAccessController.TENANT_HEADER, TENANT)
                .header(ProtectedObjectAccessController.SUBJECT_HEADER, SUBJECT)
                .header(ProtectedObjectAccessController.OBJECT_ID_HEADER, OBJECT_ID)
                .header(ProtectedObjectAccessController.ACCESS_PURPOSE_HEADER, ACCESS_PURPOSE)
                .header(ProtectedObjectAccessController.OBJECT_PURPOSE_HEADER,
                        PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE.name());
    }

    private static void assertLeakFree(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain(TOKEN, OBJECT_ID, TENANT, SUBJECT,
                        "bucket", "storage.invalid", "https://", "ciphertext", "provider");
        assertThat(result.getResponse().getHeaderNames())
                .noneMatch(name -> name.equalsIgnoreCase("Location")
                        || name.toLowerCase().contains("bucket")
                        || name.toLowerCase().contains("object-key"));
    }
}
