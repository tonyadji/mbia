package com.lehnade.mbia.shared.api.web;

import com.lehnade.mbia.api.generated.CurrentUserApi;
import com.lehnade.mbia.api.generated.model.UpdateCurrentUserRequest;
import com.lehnade.mbia.api.generated.model.UserResponse;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only implementation of a generated interface, to observe where contract paths are served.
 * A {@link TestComponent} is not scanned: it would clash with the real controller elsewhere.
 */
@TestComponent
@RestController
class CurrentUserApiStubController implements CurrentUserApi {

    @Override
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Override
    public ResponseEntity<UserResponse> updateCurrentUser(UpdateCurrentUserRequest updateCurrentUserRequest) {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
