package story.feature.exception;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import story.exception.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {
    @Mock
    private FailureService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @MethodSource("failures")
    void selectsHandlerAndReturnsSafeResponse(RuntimeException failure, int status, ErrorCode code) throws Exception {
        when(service.execute()).thenThrow(failure);
        assertError(mvc.perform(get("/test/failure")), status, code);
        verify(service).execute();
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new EntityNotFoundException("SECRET"), 404, ErrorCode.NOT_FOUND),
                Arguments.of(new DataIntegrityViolationException("SECRET"), 409, ErrorCode.DATA_CONFLICT),
                Arguments.of(new OptimisticLockingFailureException("SECRET"), 409, ErrorCode.CONCURRENT_UPDATE),
                Arguments.of(new OptimisticLockException("SECRET"), 409, ErrorCode.CONCURRENT_UPDATE),
                Arguments.of(new DataAccessResourceFailureException("SECRET"), 503, ErrorCode.SERVICE_UNAVAILABLE),
                Arguments.of(new CannotAcquireLockException("SECRET"), 503, ErrorCode.SERVICE_UNAVAILABLE),
                Arguments.of(new InvalidDataAccessApiUsageException("SECRET"), 500, ErrorCode.INTERNAL_ERROR),
                Arguments.of(new BadCredentialsException("SECRET"), 401, ErrorCode.UNAUTHORIZED),
                Arguments.of(new AccessDeniedException("SECRET"), 403, ErrorCode.FORBIDDEN),
                Arguments.of(new MissingCsrfTokenException("SECRET"), 403, ErrorCode.CSRF_INVALID),
                Arguments.of(new MultipartException("SECRET"), 400, ErrorCode.MALFORMED_REQUEST),
                Arguments.of(new MaxUploadSizeExceededException(10), 413, ErrorCode.PAYLOAD_TOO_LARGE),
                Arguments.of(new ConstraintViolationException("SECRET", Set.of()), 400, ErrorCode.VALIDATION_FAILED),
                Arguments.of(new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SECRET"),
                        422, ErrorCode.REQUEST_FAILED),
                Arguments.of(new AnnotatedFailure(), 410, ErrorCode.REQUEST_FAILED),
                Arguments.of(new NonErrorStatusFailure(), 500, ErrorCode.INTERNAL_ERROR),
                Arguments.of(new IllegalStateException("SECRET"), 500, ErrorCode.INTERNAL_ERROR));
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void handlesEveryBusinessError(ErrorCode code) throws Exception {
        when(service.execute()).thenThrow(new AppException(code, new RuntimeException("SECRET")));
        assertError(mvc.perform(get("/test/failure")), code.getStatus().value(), code);
        verify(service).execute();
    }

    @Test
    void rejectsInvalidBodyUsingBeanValidation() throws Exception {
        assertError(mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}")), 400, ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        assertError(mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                .content("{")), 400, ErrorCode.MALFORMED_REQUEST);
    }

    @Test
    void rejectsMissingBody() throws Exception {
        assertError(mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)),
                400, ErrorCode.MALFORMED_REQUEST);
    }

    @Test
    void rejectsMissingRequestParameter() throws Exception {
        assertError(mvc.perform(get("/test/parameter")), 400, ErrorCode.MISSING_PARAMETER);
    }

    @Test
    void rejectsWrongParameterType() throws Exception {
        assertError(mvc.perform(get("/test/parameter").param("id", "abc")), 400, ErrorCode.TYPE_MISMATCH);
    }

    @Test
    void rejectsMissingHeader() throws Exception {
        assertError(mvc.perform(get("/test/header")), 400, ErrorCode.MISSING_PARAMETER);
    }

    @Test
    void preservesAllowHeaderForUnsupportedMethod() throws Exception {
        assertError(mvc.perform(delete("/test/body")), 405, ErrorCode.METHOD_NOT_ALLOWED)
                .andExpect(header().string("Allow", containsString("POST")));
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        assertError(mvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("hello")),
                415, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void handlesUnknownRoute() throws Exception {
        assertError(mvc.perform(get("/missing")), 404, ErrorCode.NOT_FOUND);
    }

    @Test
    void leavesSuccessfulResponsesUnchanged() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Tai\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tai"));
        verifyNoInteractions(service);
    }

    private ResultActions assertError(ResultActions result, int statusCode, ErrorCode code) throws Exception {
        return result.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value(code.name()))
                .andExpect(jsonPath("$.error.message").value(code.getMessage()))
                .andExpect(content().string(not(containsString("SECRET"))));
    }

    interface FailureService {
        Object execute();
    }

    record Payload(@NotBlank String name) {}

    @RestController
    static class TestController {
        private final FailureService service;

        TestController(FailureService service) {
            this.service = service;
        }

        @GetMapping("/test/failure")
        Object failure() {
            return service.execute();
        }

        @PostMapping("/test/body")
        Payload body(@Valid @RequestBody Payload body) {
            return body;
        }

        @GetMapping("/test/parameter")
        Object parameter(@RequestParam("id") int id) {
            return Map.of("id", id);
        }

        @GetMapping("/test/header")
        Object header(@RequestHeader("X-Required") String value) {
            return Map.of("value", value);
        }
    }

    @ResponseStatus(code = HttpStatus.GONE, reason = "SECRET")
    static class AnnotatedFailure extends RuntimeException {}

    @ResponseStatus(HttpStatus.OK)
    static class NonErrorStatusFailure extends RuntimeException {}
}
