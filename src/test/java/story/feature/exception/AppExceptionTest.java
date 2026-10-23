package story.feature.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import story.exception.AppException;
import story.exception.ErrorCode;

import static org.junit.jupiter.api.Assertions.*;

class AppExceptionTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void preservesCodeAndDefaultMessage(ErrorCode code) {
        AppException exception = new AppException(code);
        assertSame(code, exception.getErrorCode());
        assertEquals(code.getMessage(), exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void preservesCauseWithoutExposingItsMessage() {
        var cause = new IllegalStateException("secret database credentials");
        var exception = new AppException(ErrorCode.INTERNAL_ERROR, cause);
        assertSame(cause, exception.getCause());
        assertSame(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("An internal server error occurred.", exception.getMessage());
    }

    @Test
    void rejectsNullErrorCodesInBothConstructors() {
        assertThrows(NullPointerException.class, () -> new AppException(null));
        assertThrows(NullPointerException.class, () -> new AppException(null, new RuntimeException()));
    }
}
