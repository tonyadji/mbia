package com.lehnade.mbia.identity.application.provisionuser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.domain.PreferredLocale;
import com.lehnade.mbia.identity.domain.User;
import com.lehnade.mbia.identity.domain.UserId;
import com.lehnade.mbia.identity.domain.UserRepository;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Just-in-time provisioning rules of ADR-005 and PR-10, without a database. */
class ProvisionCurrentUserUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private final InMemoryUsers users = new InMemoryUsers();
    private final ProvisionCurrentUserUseCase useCase =
            new ProvisionCurrentUserUseCase(users, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void firstCallCreatesTheUserFromTheClaims() {
        CurrentUser user = useCase.provision(command("sub-1", "a@mbia.test", true, "Alice", "en"));

        assertThat(users.inserts).isEqualTo(1);
        assertThat(user.email()).isEqualTo("a@mbia.test");
        assertThat(user.displayName()).isEqualTo("Alice");
        assertThat(user.preferredLocale()).isEqualTo("en");
        User stored = users.findBySubject("sub-1").orElseThrow();
        assertThat(stored.id().value()).isEqualTo(user.id());
        assertThat(stored.createdAt()).isEqualTo(NOW);
        assertThat(stored.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void laterCallCreatesNothing() {
        CurrentUser first = useCase.provision(command("sub-1", "a@mbia.test", true, "Alice", "fr"));
        CurrentUser second = useCase.provision(command("sub-1", "a@mbia.test", true, "Alice", "fr"));

        assertThat(users.inserts).isEqualTo(1);
        assertThat(users.saves).isZero();
        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void localeFallsBackToFrench() {
        assertThat(useCase.provision(command("sub-de", "de@mbia.test", true, null, "de")).preferredLocale())
                .isEqualTo("fr");
        assertThat(useCase.provision(command("sub-null", "n@mbia.test", true, null, null)).preferredLocale())
                .isEqualTo("fr");
        assertThat(useCase.provision(command("sub-fr", "f@mbia.test", true, null, "fr")).preferredLocale())
                .isEqualTo("fr");
    }

    @Test
    void emailChangeUpdatesTheEmailOnlyAndKeepsTheDisplayNameAndLocale() {
        useCase.provision(command("sub-1", "old@mbia.test", true, "Alice", "fr"));

        CurrentUser user = useCase.provision(command("sub-1", "new@mbia.test", true, "Renamed", "en"));

        assertThat(user.email()).isEqualTo("new@mbia.test");
        assertThat(user.displayName()).isEqualTo("Alice");
        assertThat(user.preferredLocale()).isEqualTo("fr");
        assertThat(users.saves).isEqualTo(1);
    }

    @Test
    void unverifiedEmailIsRejectedAndNothingIsWritten() {
        assertThatThrownBy(() -> useCase.provision(command("sub-1", "a@mbia.test", false, "Alice", "fr")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
        assertThat(users.inserts).isZero();
    }

    @Test
    void missingEmailIsTreatedAsUnverified() {
        assertThatThrownBy(() -> useCase.provision(command("sub-1", null, true, "Alice", "fr")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
        assertThat(users.inserts).isZero();
    }

    @Test
    void lostInsertRaceReadsTheExistingUser() {
        // Another request inserted the same subject between our lookup and our insert.
        User winner = User.register(UserId.newId(), "sub-1", "a@mbia.test", "Winner", PreferredLocale.FR, NOW);
        users.rowInsertedConcurrently = winner;

        CurrentUser user = useCase.provision(command("sub-1", "a@mbia.test", true, "Loser", "en"));

        assertThat(user.id()).isEqualTo(winner.id().value());
        assertThat(user.displayName()).isEqualTo("Winner");
        assertThat(users.bySubject).hasSize(1);
    }

    private static ProvisionCurrentUserCommand command(
            String subject, String email, boolean emailVerified, String name, String locale) {
        return new ProvisionCurrentUserCommand(subject, email, emailVerified, name, locale);
    }

    /** Behaves like the unique constraint on the subject; can simulate a concurrent insert. */
    private static final class InMemoryUsers implements UserRepository {

        final Map<String, User> bySubject = new HashMap<>();
        User rowInsertedConcurrently;
        int inserts;
        int saves;

        @Override
        public Optional<User> findBySubject(String subject) {
            return Optional.ofNullable(bySubject.get(subject));
        }

        @Override
        public Optional<User> findById(UserId id) {
            return bySubject.values().stream().filter(user -> user.id().equals(id)).findFirst();
        }

        @Override
        public boolean insertIfAbsent(User user) {
            if (rowInsertedConcurrently != null) {
                bySubject.put(rowInsertedConcurrently.identityProviderSubject(), rowInsertedConcurrently);
                rowInsertedConcurrently = null;
            }
            if (bySubject.containsKey(user.identityProviderSubject())) {
                return false;
            }
            bySubject.put(user.identityProviderSubject(), user);
            inserts++;
            return true;
        }

        @Override
        public void save(User user) {
            bySubject.put(user.identityProviderSubject(), user);
            saves++;
        }
    }
}
