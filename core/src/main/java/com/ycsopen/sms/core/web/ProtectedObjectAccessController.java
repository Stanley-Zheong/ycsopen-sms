package com.ycsopen.sms.core.web;

import com.ycsopen.sms.core.common.security.object.PrivateObjectStorePort;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Application-mediated binary access; no storage locator or direct object URL is exposed. */
@RestController
@ConditionalOnBean(ProtectedObjectService.class)
@RequestMapping(ProtectedObjectAccessController.BASE_PATH)
public final class ProtectedObjectAccessController {

    public static final String BASE_PATH = "/api/v1/protected-objects";
    public static final String CAPABILITY_ROUTE = "/capabilities/{capability}";
    public static final String TENANT_HEADER = "X-Tenant-Scope";
    public static final String SUBJECT_HEADER = "X-Subject";
    public static final String OBJECT_ID_HEADER = "X-Protected-Object-Id";
    public static final String ACCESS_PURPOSE_HEADER = "X-Access-Purpose";
    public static final String OBJECT_PURPOSE_HEADER = "X-Object-Purpose";

    private final ProtectedObjectService protectedObjectService;

    public ProtectedObjectAccessController(ProtectedObjectService protectedObjectService) {
        this.protectedObjectService = protectedObjectService;
    }

    /** The byte array exists only after the service has validated checksum and the complete GCM tag. */
    @GetMapping(CAPABILITY_ROUTE)
    public ResponseEntity<byte[]> read(
            @PathVariable String capability,
            @RequestHeader(TENANT_HEADER) String tenantScope,
            @RequestHeader(SUBJECT_HEADER) String subject,
            @RequestHeader(OBJECT_ID_HEADER) String protectedObjectId,
            @RequestHeader(ACCESS_PURPOSE_HEADER) String accessPurpose,
            @RequestHeader(OBJECT_PURPOSE_HEADER) String objectPurpose) {
        PrivateObjectStorePort.ObjectPurpose parsedPurpose = parsePurpose(objectPurpose);
        ProtectedObjectService.ProtectedObjectData data = protectedObjectService.read(
                new ProtectedObjectService.ReadRequest(protectedObjectId, capability,
                        tenantScope, subject, accessPurpose, parsedPurpose));
        byte[] body = data.bytes();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(data.mediaType()))
                .contentLength(body.length)
                .body(body);
    }

    @ExceptionHandler(ProtectedObjectService.Failure.class)
    ResponseEntity<AccessError> handleProtectedObjectFailure(ProtectedObjectService.Failure failure) {
        HttpStatus status = switch (failure.category()) {
            case PROTECTED_OBJECT_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case PROTECTED_OBJECT_INPUT_INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
            case PROTECTED_OBJECT_INTEGRITY_INVALID, PROTECTED_OBJECT_UNAVAILABLE ->
                    HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new AccessError(failure.category().name(), failure.getMessage()));
    }

    private static PrivateObjectStorePort.ObjectPurpose parsePurpose(String value) {
        try {
            return PrivateObjectStorePort.ObjectPurpose.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw invalidInput();
        }
    }

    private static ProtectedObjectService.Failure invalidInput() {
        return ProtectedObjectService.Failure.invalidInput();
    }

    record AccessError(String code, String message) {
    }
}
