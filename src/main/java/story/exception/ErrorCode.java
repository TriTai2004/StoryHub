package story.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request data failed validation."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request body is missing or malformed."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "A required parameter, header, or request part is missing."),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "Invalid parameter type."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "CSRF token is missing or invalid."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "The requested response format is not supported."),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current resource state."),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Data is duplicated or violates a constraint."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "The data has changed. Please reload and try again."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "The uploaded data exceeds the maximum allowed size."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type is not supported."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later."),
    REQUEST_FAILED(HttpStatus.BAD_REQUEST, "The request could not be completed."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable."),
    REQUEST_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "Request processing timed out.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public static ErrorCode fromStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> TOO_MANY_REQUESTS;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> status.is4xxClientError() ? REQUEST_FAILED : INTERNAL_ERROR;
        };
    }
}
