package story.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Shared response body; the controller sets the HTTP status separately. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public ApiResponse {
        if (success && error != null) {
            throw new IllegalArgumentException("A successful response cannot contain an error");
        }
        if (!success && (data != null || error == null)) {
            throw new IllegalArgumentException("A failed response must contain an error and no data");
        }
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> empty() {
        return success(null);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message));
    }

    /** A stable application error code and a message safe to show to clients. */
    public record ApiError(String code, String message) {

        public ApiError {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("Error code must not be blank");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("Error message must not be blank");
            }
        }
    }
}
