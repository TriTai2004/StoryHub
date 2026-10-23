package story.feature.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatusCode;
import story.exception.ErrorCode;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @ParameterizedTest
    @CsvSource({
            "400, BAD_REQUEST", "401, UNAUTHORIZED", "403, FORBIDDEN", "404, NOT_FOUND",
            "405, METHOD_NOT_ALLOWED", "406, NOT_ACCEPTABLE", "409, CONFLICT",
            "413, PAYLOAD_TOO_LARGE", "415, UNSUPPORTED_MEDIA_TYPE", "429, TOO_MANY_REQUESTS",
            "503, SERVICE_UNAVAILABLE", "422, REQUEST_FAILED", "499, REQUEST_FAILED",
            "500, INTERNAL_ERROR", "502, INTERNAL_ERROR", "599, INTERNAL_ERROR"
    })
    void mapsHttpStatusToErrorCode(int status, ErrorCode expected) {
        assertEquals(expected, ErrorCode.fromStatus(HttpStatusCode.valueOf(status)));
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void definesAnErrorStatusAndEnglishMessage(ErrorCode code) {
        assertTrue(code.getStatus().isError());
        assertFalse(code.getMessage().isBlank());
        assertTrue(code.getMessage().chars().allMatch(character -> character >= 32 && character < 127));
    }
}
