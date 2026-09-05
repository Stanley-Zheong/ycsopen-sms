package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;

/** Fixed private multipart surface for staged tenant-registration evidence. */
@RestController
@ConditionalOnBean(TenantRegistrationObjectSessionService.class)
@RequestMapping(TenantRegistrationObjectController.BASE_PATH)
public final class TenantRegistrationObjectController {

    public static final String BASE_PATH = "/api/v1/console/tenants/registration-object-sessions";
    public static final String SESSION_ROUTE = "";
    public static final String UPLOAD_ROUTE = "/{sessionId}/objects/{purpose}";
    public static final String CLOSE_ROUTE = "/{sessionId}";
    public static final String FILE_PART = "file";

    private final TenantRegistrationObjectSessionService sessionService;

    public TenantRegistrationObjectController(
            TenantRegistrationObjectSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping(SESSION_ROUTE)
    public ResponseEntity<ApiResponse<TenantRegistrationObjectSessionService.CreatedSession>> create() {
        return privateOk(sessionService.createSession());
    }

    @PostMapping(value = UPLOAD_ROUTE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TenantRegistrationObjectSessionService.UploadedObject>> upload(
            @PathVariable String sessionId,
            @PathVariable String purpose,
            @RequestHeader(TenantRegistrationObjectSessionService.UPLOAD_TOKEN_HEADER)
            String uploadToken,
            @RequestPart(FILE_PART) MultipartFile file,
            HttpServletRequest servletRequest) {
        requireExactlyOneFilePart(servletRequest);
        TenantRegistrationObjectSessionService.UploadPurpose parsedPurpose =
                TenantRegistrationObjectSessionService.UploadPurpose.parse(purpose)
                        .orElseThrow(TenantRegistrationObjectSessionService.Failure::inputInvalid);
        try {
            return privateOk(sessionService.upload(
                    new TenantRegistrationObjectSessionService.UploadRequest(
                            sessionId, uploadToken, parsedPurpose, file.getContentType(),
                            file.getInputStream(), file.getSize())));
        } catch (IOException failure) {
            throw TenantRegistrationObjectSessionService.Failure.inputInvalid();
        }
    }

    @DeleteMapping(CLOSE_ROUTE)
    public ResponseEntity<ApiResponse<SessionStateResponse>> close(
            @PathVariable String sessionId,
            @RequestHeader(TenantRegistrationObjectSessionService.UPLOAD_TOKEN_HEADER)
            String uploadToken) {
        TenantRegistrationObjectSessionService.SessionState state =
                sessionService.close(sessionId, uploadToken);
        return privateOk(new SessionStateResponse(state));
    }

    @ExceptionHandler(TenantRegistrationObjectSessionService.Failure.class)
    ResponseEntity<RegistrationObjectError> handleRegistrationObjectFailure(
            TenantRegistrationObjectSessionService.Failure failure) {
        HttpStatus status = switch (failure.category()) {
            case REGISTRATION_UPLOAD_INPUT_INVALID,
                    REGISTRATION_UPLOAD_SIGNATURE_MISMATCH -> HttpStatus.UNPROCESSABLE_ENTITY;
            case REGISTRATION_UPLOAD_TOKEN_INVALID -> HttpStatus.FORBIDDEN;
            case REGISTRATION_UPLOAD_SESSION_NOT_OPEN -> HttpStatus.CONFLICT;
            case REGISTRATION_UPLOAD_SESSION_EXPIRED -> HttpStatus.GONE;
            case REGISTRATION_UPLOAD_MEDIA_TYPE_NOT_ACCEPTED -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case REGISTRATION_UPLOAD_SIZE_LIMIT_EXCEEDED -> HttpStatus.PAYLOAD_TOO_LARGE;
            case REGISTRATION_UPLOAD_LIMIT_REACHED -> HttpStatus.TOO_MANY_REQUESTS;
            case REGISTRATION_UPLOAD_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new RegistrationObjectError(failure.category().name(), failure.getMessage()));
    }

    private static void requireExactlyOneFilePart(HttpServletRequest request) {
        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            int fileCount = multipartRequest.getMultiFileMap().values().stream()
                    .mapToInt(java.util.List::size).sum();
            if (fileCount != 1 || multipartRequest.getMultiFileMap().size() != 1
                    || !multipartRequest.getMultiFileMap().containsKey(FILE_PART)
                    || !multipartRequest.getParameterMap().isEmpty()) {
                throw TenantRegistrationObjectSessionService.Failure.inputInvalid();
            }
            return;
        }
        try {
            java.util.Collection<Part> parts = request.getParts();
            if (parts.size() != 1) {
                throw TenantRegistrationObjectSessionService.Failure.inputInvalid();
            }
            Part only = parts.iterator().next();
            if (!FILE_PART.equals(only.getName()) || only.getSubmittedFileName() == null) {
                throw TenantRegistrationObjectSessionService.Failure.inputInvalid();
            }
        } catch (IOException | ServletException failure) {
            throw TenantRegistrationObjectSessionService.Failure.inputInvalid();
        }
    }

    private static <T> ResponseEntity<ApiResponse<T>> privateOk(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(ApiResponse.ok(body));
    }

    public record SessionStateResponse(TenantRegistrationObjectSessionService.SessionState state) {
    }

    record RegistrationObjectError(String code, String message) {
    }
}
