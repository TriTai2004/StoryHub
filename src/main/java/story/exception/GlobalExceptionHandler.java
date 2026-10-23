package story.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import story.common.dto.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = resolveMvcError(ex, statusCode);
        return respond(ex, code, headers, statusCode, request);
    }

    @ExceptionHandler(AppException.class)
    public @Nullable ResponseEntity<Object> handleAppException(AppException ex, WebRequest request) {
        return respond(ex, ex.getErrorCode(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public @Nullable ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        boolean returnValueViolation = ex.getConstraintViolations().stream().anyMatch(violation -> {
            for (var node : violation.getPropertyPath()) {
                if (node.getKind() == ElementKind.RETURN_VALUE) {
                    return true;
                }
            }
            return false;
        });
        return respond(ex, returnValueViolation ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_FAILED, request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public @Nullable ResponseEntity<Object> handleEntityNotFound(EntityNotFoundException ex, WebRequest request) {
        return respond(ex, ErrorCode.NOT_FOUND, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public @Nullable ResponseEntity<Object> handleDataConflict(DataIntegrityViolationException ex, WebRequest request) {
        return respond(ex, ErrorCode.DATA_CONFLICT, request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public @Nullable ResponseEntity<Object> handleConcurrentUpdate(Exception ex, WebRequest request) {
        return respond(ex, ErrorCode.CONCURRENT_UPDATE, request);
    }

    @ExceptionHandler(DataAccessException.class)
    public @Nullable ResponseEntity<Object> handleDataAccess(DataAccessException ex, WebRequest request) {
        ErrorCode code = ex instanceof TransientDataAccessException
                || ex instanceof DataAccessResourceFailureException
                ? ErrorCode.SERVICE_UNAVAILABLE : ErrorCode.INTERNAL_ERROR;
        return respond(ex, code, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public @Nullable ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return respond(ex, ErrorCode.UNAUTHORIZED, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public @Nullable ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return respond(ex, ex instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN, request);
    }

    @ExceptionHandler(MultipartException.class)
    public @Nullable ResponseEntity<Object> handleMultipart(MultipartException ex, WebRequest request) {
        return respond(ex, ErrorCode.MALFORMED_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public @Nullable ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        ResponseStatus annotation = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        HttpStatusCode status = annotation != null && annotation.code().isError()
                ? annotation.code() : ErrorCode.INTERNAL_ERROR.getStatus();
        return respond(ex, ErrorCode.fromStatus(status), new HttpHeaders(), status, request);
    }

    private ErrorCode resolveMvcError(Exception ex, HttpStatusCode status) {
        if (ex instanceof AsyncRequestTimeoutException) {
            return ErrorCode.REQUEST_TIMEOUT;
        }
        if (status.is5xxServerError()) {
            return ErrorCode.fromStatus(status);
        }
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException) {
            return ErrorCode.VALIDATION_FAILED;
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return ErrorCode.MALFORMED_REQUEST;
        }
        if (ex instanceof ServletRequestBindingException || ex instanceof MissingServletRequestPartException) {
            return ErrorCode.MISSING_PARAMETER;
        }
        if (ex instanceof TypeMismatchException) {
            return ErrorCode.TYPE_MISMATCH;
        }
        return ErrorCode.fromStatus(status);
    }

    private @Nullable ResponseEntity<Object> respond(Exception ex, ErrorCode code, WebRequest request) {
        return respond(ex, code, new HttpHeaders(), code.getStatus(), request);
    }

    private @Nullable ResponseEntity<Object> respond(Exception ex, ErrorCode code,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request failed: code={}, status={}", code.name(), status.value(), ex);
        }
        return super.handleExceptionInternal(ex,
                ApiResponse.failure(code.name(), code.getMessage()), headers, status, request);
    }
}
