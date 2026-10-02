package com.lehnade.mbia.memory.api;

import com.lehnade.mbia.memory.domain.MediaAsset;
import com.lehnade.mbia.shared.api.error.GlobalExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * The contract bounds {@code sizeBytes} to 15 MB and names the refusal {@code MEDIA_TOO_LARGE}
 * (openapi {@code CreateMediaUploadRequest}): the bean validation of that bound answers with this
 * code instead of {@code VALIDATION_FAILED} when it is the only error. Every other error is
 * answered by the {@link GlobalExceptionHandler} as usual.
 */
@RestControllerAdvice(assignableTypes = MediaController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class MediaUploadSizeAdvice {

    private final GlobalExceptionHandler problems;

    MediaUploadSizeAdvice(GlobalExceptionHandler problems) {
        this.problems = problems;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Object> handleInvalidRequest(MethodArgumentNotValidException ex, WebRequest request)
            throws Exception {
        if (ex.getErrorCount() == 1 && isSizeAboveMaximum(ex.getBindingResult().getFieldError())) {
            return problems.handleDomainException(MediaAsset.tooLarge(), request);
        }
        return problems.handleException(ex, request);
    }

    private static boolean isSizeAboveMaximum(FieldError error) {
        return error != null && error.getField().equals("sizeBytes") && "Max".equals(error.getCode());
    }
}
