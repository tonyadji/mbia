package com.lehnade.mbia.shared.api.error;

import com.lehnade.mbia.shared.api.tracing.RequestIdFilter;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.FieldValidationException;
import com.lehnade.mbia.shared.domain.Versions;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into {@code application/problem+json} shaped as the {@code ProblemDetails}
 * schema of {@code openapi.yaml} ({@code technical-specification.md} §12).
 *
 * <p>Only the stable code, a generic title and a safe detail reach the client. Messages of
 * framework or unexpected exceptions are never returned; unexpected errors are only logged.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern WORD_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

    private final String problemsBaseUri;

    GlobalExceptionHandler(@Value("${mbia.problems.base-uri}") String problemsBaseUri) {
        this.problemsBaseUri = problemsBaseUri.replaceAll("/+$", "");
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> handleDomainException(DomainException ex, WebRequest request) {
        if (ex instanceof FieldValidationException invalid) {
            List<FieldErrorResponse> fieldErrors =
                    List.of(new FieldErrorResponse(invalid.field(), invalid.fieldCode(), invalid.detail()));
            return respond(problem(ex.code(), ex.detail(), fieldErrors, null), new HttpHeaders(), request);
        }
        Map<String, Object> details = ex.details().isEmpty() ? null : ex.details();
        return respond(problem(ex.code(), ex.detail(), null, details), new HttpHeaders(), request);
    }

    /**
     * The row changed between the use case's version check and its write (the JPA {@code @Version}
     * guard): the same conflict as a stale {@code If-Match} (architecture.md §12).
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLockingFailure(OptimisticLockingFailureException ex, WebRequest request) {
        return respond(generic(ErrorCode.CONCURRENT_MODIFICATION), new HttpHeaders(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<FieldErrorResponse> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(
                        violation.getPropertyPath().toString(),
                        upperSnake(violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()),
                        violation.getMessage()))
                .toList();
        return validationFailed(fieldErrors, new HttpHeaders(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error on {}", describe(request), ex);
        return respond(generic(ErrorCode.INTERNAL_ERROR), new HttpHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .toList();
        return validationFailed(fieldErrors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> fieldErrors = ex.getParameterValidationResults().stream()
                .flatMap(GlobalExceptionHandler::fieldErrors)
                .toList();
        return validationFailed(fieldErrors, headers, request);
    }

    /** Every other exception known to Spring MVC: unreadable body, unknown path, wrong method… */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = codeFor(statusCode);
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("Unexpected error on {}", describe(request), ex);
        }
        return respond(generic(code), headers, request);
    }

    private ResponseEntity<Object> validationFailed(
            List<FieldErrorResponse> fieldErrors, HttpHeaders headers, WebRequest request) {
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return respond(problem(code, genericDetail(code), fieldErrors, null), headers, request);
    }

    private ProblemResponse generic(ErrorCode code) {
        return problem(code, genericDetail(code), null, null);
    }

    private ProblemResponse problem(
            ErrorCode code, String detail, List<FieldErrorResponse> fieldErrors, Map<String, Object> details) {
        String type = problemsBaseUri + "/" + code.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return new ProblemResponse(type, code.title(), code.httpStatus(), code.name(), detail,
                RequestIdFilter.currentRequestId(), fieldErrors, details);
    }

    private ResponseEntity<Object> respond(ProblemResponse problem, HttpHeaders headers, WebRequest request) {
        if (problem.status() < 500) {
            log.info("Request rejected on {}: {} {}", describe(request), problem.status(), problem.code());
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return ResponseEntity.status(problem.status()).headers(responseHeaders).body(problem);
    }

    private static Stream<FieldErrorResponse> fieldErrors(ParameterValidationResult result) {
        if (result instanceof ParameterErrors errors) {
            return errors.getFieldErrors().stream().map(GlobalExceptionHandler::fieldError);
        }
        String parameter = result.getMethodParameter().getParameterName();
        return result.getResolvableErrors().stream().map(error -> {
            String[] codes = error.getCodes();
            String constraint = codes == null || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
            return new FieldErrorResponse(parameter, upperSnake(constraint), error.getDefaultMessage());
        });
    }

    private static FieldErrorResponse fieldError(FieldError error) {
        return new FieldErrorResponse(error.getField(), upperSnake(error.getCode()), error.getDefaultMessage());
    }

    /** {@code NotBlank} → {@code NOT_BLANK}. */
    private static String upperSnake(String constraint) {
        return WORD_BOUNDARY.matcher(constraint).replaceAll("$1_$2").toUpperCase(Locale.ROOT);
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? ErrorCode.VALIDATION_FAILED : ErrorCode.INTERNAL_ERROR;
        };
    }

    private static String genericDetail(ErrorCode code) {
        return switch (code) {
            case VALIDATION_FAILED -> "The request is invalid.";
            case RESOURCE_NOT_FOUND -> "No resource exists at this path.";
            case METHOD_NOT_ALLOWED -> "This HTTP method is not supported for this path.";
            case NOT_ACCEPTABLE -> "No acceptable representation is available.";
            case UNSUPPORTED_MEDIA_TYPE -> "This content type is not supported.";
            case CONCURRENT_MODIFICATION -> Versions.STALE_DETAIL;
            default -> "An unexpected error occurred.";
        };
    }

    private static String describe(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getHttpMethod() + " " + servletRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }
}
