package com.lehnade.mbia.identity.application.provisionuser;

import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.domain.PreferredLocale;
import com.lehnade.mbia.identity.domain.User;
import com.lehnade.mbia.identity.domain.UserId;
import com.lehnade.mbia.identity.domain.UserRepository;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds or creates the Mbia User of an authenticated caller, just in time (ADR-005, data-model.md §5).
 *
 * <ul>
 *   <li>A token without a verified email is rejected with {@code EMAIL_NOT_VERIFIED}.
 *   <li>The first call creates the User from the subject, email, name and locale claims.
 *   <li>Later calls only follow a change of email; the display name and language are the user's.
 *   <li>Concurrent first calls for one subject create one User: the losing insert is a no-op and
 *       the winner's row is read back.
 * </ul>
 */
@Service
public class ProvisionCurrentUserUseCase {

    private final UserRepository users;
    private final Clock clock;

    public ProvisionCurrentUserUseCase(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public CurrentUser provision(ProvisionCurrentUserCommand command) {
        if (!command.emailVerified() || command.email() == null || command.email().isBlank()) {
            throw new DomainException(ErrorCode.EMAIL_NOT_VERIFIED, "The email address of this account is not verified.");
        }
        User user = users.findBySubject(command.subject()).orElseGet(() -> register(command));
        if (user.syncEmail(command.email(), clock.instant())) {
            users.save(user);
        }
        return CurrentUser.of(user);
    }

    private User register(ProvisionCurrentUserCommand command) {
        User candidate = User.register(UserId.newId(), command.subject(), command.email(), command.name(),
                PreferredLocale.fromClaim(command.locale()), clock.instant());
        if (users.insertIfAbsent(candidate)) {
            return candidate;
        }
        // Not found: the email belongs to another account (open question OQ-003).
        return users.findBySubject(command.subject())
                .orElseThrow(() -> new IllegalStateException("The email of this token is used by another user"));
    }
}
