package com.lehnade.mbia.identity.api;

import com.lehnade.mbia.api.generated.CurrentUserApi;
import com.lehnade.mbia.api.generated.model.Locale;
import com.lehnade.mbia.api.generated.model.UpdateCurrentUserRequest;
import com.lehnade.mbia.api.generated.model.UserResponse;
import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.identity.application.updatecurrentuser.UpdateCurrentUserCommand;
import com.lehnade.mbia.identity.application.updatecurrentuser.UpdateCurrentUserUseCase;
import com.lehnade.mbia.identity.domain.PreferredLocale;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CurrentUserController implements CurrentUserApi {

    private final CurrentUserAccessor currentUserAccessor;
    private final UpdateCurrentUserUseCase updateCurrentUser;

    CurrentUserController(CurrentUserAccessor currentUserAccessor, UpdateCurrentUserUseCase updateCurrentUser) {
        this.currentUserAccessor = currentUserAccessor;
        this.updateCurrentUser = updateCurrentUser;
    }

    /** The User was provisioned by {@link CurrentUserInterceptor} before this call. */
    @Override
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(toResponse(currentUserAccessor.currentUser()));
    }

    @Override
    public ResponseEntity<UserResponse> updateCurrentUser(UpdateCurrentUserRequest request) {
        var command = new UpdateCurrentUserCommand(
                currentUserAccessor.currentUser().id(),
                Optional.ofNullable(request.getDisplayName()),
                Optional.ofNullable(request.getPreferredLocale())
                        .map(locale -> PreferredLocale.ofCode(locale.getValue()).orElseThrow()));
        return ResponseEntity.ok(toResponse(updateCurrentUser.update(command)));
    }

    private static UserResponse toResponse(CurrentUser user) {
        return new UserResponse(user.id(), user.email(), Locale.fromValue(user.preferredLocale()))
                .displayName(user.displayName());
    }
}
