package com.mohan.walletflow.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolationException;

/**
 * Maps every error to RFC 9457 problem+json. Spring MVC's own exceptions are handled by
 * {@link ResponseEntityExceptionHandler}; we only enrich them with a stable {@code code}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail problem = Problems.of(ex.getStatus(), ex.getCode(), ex.getStatus().getReasonPhrase(), ex.getMessage());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus());
        ex.getHeaders().forEach(builder::header);
        return builder.body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
                "One or more parameters are invalid");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "invalid-parameter", "Invalid parameter",
                "Parameter '" + ex.getName() + "' has an invalid value");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ProblemDetail> handleConcurrency(ConcurrencyFailureException ex) {
        log.warn("Concurrency failure after retries: {}", ex.getMessage());
        ProblemDetail problem = Problems.of(HttpStatus.CONFLICT, "concurrent-modification", "Conflict",
                "The resource was modified concurrently, please retry");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = Problems.of(HttpStatus.CONFLICT, "data-conflict", "Conflict",
                "The request conflicts with the current state of the resource");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = Problems.of(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                "You do not have permission to perform this action");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        ProblemDetail problem = Problems.of(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        ProblemDetail problem = Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "An unexpected error occurred");
        return ResponseEntity.internalServerError().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge -> errors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
                "Request body has invalid fields");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> {
            String name = r.getMethodParameter().getParameterName();
            r.getResolvableErrors().forEach(e -> errors.putIfAbsent(name, e.getDefaultMessage()));
        });
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
                "One or more parameters are invalid");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            org.springframework.web.bind.ServletRequestBindingException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        if (ex instanceof MissingRequestHeaderException mrh) {
            ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "missing-header", "Missing header",
                    "Required header '" + mrh.getHeaderName() + "' is missing");
            return ResponseEntity.badRequest().body(problem);
        }
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd && pd.getProperties() == null) {
            String code = statusCode.value() == 400 ? "bad-request" : "http-" + statusCode.value();
            pd.setType(java.net.URI.create(Problems.TYPE_PREFIX + code));
            pd.setProperty("code", code);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
