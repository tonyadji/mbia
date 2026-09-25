package com.lehnade.mbia.identity.application.updatecurrentuser;

import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.domain.User;
import com.lehnade.mbia.identity.domain.UserId;
import com.lehnade.mbia.identity.domain.UserRepository;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Changes the current user's display name and/or language (openapi {@code updateCurrentUser}). */
@Service
public class UpdateCurrentUserUseCase {

    private final UserRepository users;
    private final Clock clock;

    public UpdateCurrentUserUseCase(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public CurrentUser update(UpdateCurrentUserCommand command) {
        if (command.displayName().isEmpty() && command.preferredLocale().isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "At least one field must be provided.");
        }
        User user = users.findById(new UserId(command.userId()))
                .orElseThrow(() -> new IllegalStateException("The current user does not exist"));
        command.displayName().ifPresent(name -> user.changeDisplayName(name, clock.instant()));
        command.preferredLocale().ifPresent(locale -> user.changePreferredLocale(locale, clock.instant()));
        users.save(user);
        return CurrentUser.of(user);
    }
}
