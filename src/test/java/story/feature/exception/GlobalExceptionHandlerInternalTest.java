package story.feature.exception;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import story.common.dto.ApiResponse;
import story.exception.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerInternalTest {
    private final ExposedHandler handler = new ExposedHandler();
    private MockHttpServletRequest servletRequest;
    private MockHttpServletResponse servletResponse;
    private ServletWebRequest request;

    @BeforeEach
    void setUp() {
        servletRequest = new MockHttpServletRequest();
        servletResponse = new MockHttpServletResponse();
        request = new ServletWebRequest(servletRequest, servletResponse);
    }

    @ParameterizedTest
    @MethodSource("mvcFailures")
    void classifiesMvcExceptionsAndPreservesStatus(Exception failure, int status, ErrorCode expected) {
        var response = handler.handle(failure, new HttpHeaders(), HttpStatusCode.valueOf(status), request);
        assertError(response, status, expected);
    }

    static Stream<Arguments> mvcFailures() {
        return Stream.of(
                Arguments.of(new AsyncRequestTimeoutException(), 503, ErrorCode.REQUEST_TIMEOUT),
                Arguments.of(mock(MethodArgumentNotValidException.class), 400, ErrorCode.VALIDATION_FAILED),
                Arguments.of(mock(HandlerMethodValidationException.class), 400, ErrorCode.VALIDATION_FAILED),
                Arguments.of(mock(HandlerMethodValidationException.class), 500, ErrorCode.INTERNAL_ERROR),
                Arguments.of(mock(HttpMessageNotReadableException.class), 400, ErrorCode.MALFORMED_REQUEST),
                Arguments.of(new ServletRequestBindingException("SECRET"), 400, ErrorCode.MISSING_PARAMETER),
                Arguments.of(new MissingServletRequestPartException("file"), 400, ErrorCode.MISSING_PARAMETER),
                Arguments.of(new TypeMismatchException("abc", Integer.class), 400, ErrorCode.TYPE_MISMATCH),
                Arguments.of(new ConversionNotSupportedException("abc", Integer.class, null),
                        500, ErrorCode.INTERNAL_ERROR),
                Arguments.of(new HttpMessageNotWritableException("SECRET"), 500, ErrorCode.INTERNAL_ERROR),
                Arguments.of(new RuntimeException("SECRET"), 406, ErrorCode.NOT_ACCEPTABLE),
                Arguments.of(new RuntimeException("SECRET"), 429, ErrorCode.TOO_MANY_REQUESTS),
                Arguments.of(new RuntimeException("SECRET"), 502, ErrorCode.INTERNAL_ERROR));
    }

    @ParameterizedTest
    @EnumSource(value = ElementKind.class, names = {"PARAMETER", "PROPERTY", "RETURN_VALUE"})
    @SuppressWarnings("unchecked")
    void distinguishesInputConstraintViolationsFromReturnValueViolations(ElementKind kind) {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        Path.Node node = mock(Path.Node.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(path.iterator()).thenReturn(List.of(node).iterator());
        when(node.getKind()).thenReturn(kind);
        var exception = new ConstraintViolationException("SECRET", Set.of(violation));

        var response = handler.handleConstraintViolation(exception, request);

        boolean returnValue = kind == ElementKind.RETURN_VALUE;
        assertError(response, returnValue ? 500 : 400,
                returnValue ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_FAILED);
        verify(violation).getPropertyPath();
        verify(node).getKind();
    }

    @Test
    void preservesFrameworkHeaders() {
        var headers = new HttpHeaders();
        headers.setAllow(Set.of(HttpMethod.GET));
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.RETRY_AFTER, "30");

        var response = handler.handle(new RuntimeException(), headers, HttpStatus.TOO_MANY_REQUESTS, request);

        assertError(response, 429, ErrorCode.TOO_MANY_REQUESTS);
        assertEquals(Set.of(HttpMethod.GET), response.getHeaders().getAllow());
        assertEquals(List.of(MediaType.APPLICATION_JSON), response.getHeaders().getAccept());
        assertEquals("30", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void leavesCommittedResponsesUntouched() {
        servletResponse.setStatus(202);
        servletResponse.setCommitted(true);

        assertNull(handler.handleAppException(new AppException(ErrorCode.NOT_FOUND), request));
        assertEquals(202, servletResponse.getStatus());
        assertEquals(0, servletResponse.getContentAsByteArray().length);
    }

    @Test
    void logsUnexpectedFailuresWithoutExposingDetailsInResponse() {
        var exception = new IllegalStateException("SECRET");
        var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertError(handler.handleUnexpected(exception, request), 500, ErrorCode.INTERNAL_ERROR);
            assertEquals(1, appender.list.size());
            var event = appender.list.getFirst();
            assertEquals("Request failed: code=INTERNAL_ERROR, status=500", event.getFormattedMessage());
            assertEquals("SECRET", event.getThrowableProxy().getMessage());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void assertError(ResponseEntity<Object> response, int status, ErrorCode code) {
        assertNotNull(response);
        assertEquals(status, response.getStatusCode().value());
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertFalse(body.success());
        assertNull(body.data());
        assertNotNull(body.error());
        assertEquals(code.name(), body.error().code());
        assertEquals(code.getMessage(), body.error().message());
        assertFalse(body.error().message().contains("SECRET"));
    }

    static class ExposedHandler extends GlobalExceptionHandler {
        ResponseEntity<Object> handle(Exception exception, HttpHeaders headers,
                HttpStatusCode status, WebRequest request) {
            return handleExceptionInternal(exception, null, headers, status, request);
        }
    }
}
