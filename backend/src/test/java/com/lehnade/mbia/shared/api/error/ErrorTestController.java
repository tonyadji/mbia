package com.lehnade.mbia.shared.api.error;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints exercising each error path of {@link GlobalExceptionHandler}. */
@RestController
@RequestMapping("/test/errors")
public class ErrorTestController {

    public static final String LEAKY_MESSAGE = "SELECT * FROM persons WHERE secret = 'hunter2'";

    record Payload(@NotBlank String name) {}

    @GetMapping(path = "/ok", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> ok() {
        return Map.of("status", "ok");
    }

    @GetMapping("/domain")
    void domain() {
        throw new DomainException(ErrorCode.FAMILY_NOT_FOUND, "Family 42 does not exist.", Map.of("familyId", "42"));
    }

    @PostMapping(path = "/validation", consumes = MediaType.APPLICATION_JSON_VALUE)
    void validation(@Valid @RequestBody Payload payload) {}

    @GetMapping("/parameter")
    void parameter(@RequestParam @Min(1) int size) {}

    @GetMapping("/optimistic-lock")
    void optimisticLock() {
        throw new OptimisticLockingFailureException(LEAKY_MESSAGE);
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException(LEAKY_MESSAGE);
    }
}
