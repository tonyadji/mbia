package com.lehnade.mbia.identity.infrastructure.persistence;

import com.lehnade.mbia.identity.domain.PreferredLocale;
import com.lehnade.mbia.identity.domain.User;
import com.lehnade.mbia.identity.domain.UserId;
import com.lehnade.mbia.identity.domain.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserRepository implements UserRepository {

    private final UserJpaRepository jpa;

    JpaUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findBySubject(String identityProviderSubject) {
        return jpa.findByIdentityProviderSubject(identityProviderSubject).map(JpaUserRepository::toDomain);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(JpaUserRepository::toDomain);
    }

    @Override
    public boolean insertIfAbsent(User user) {
        return jpa.insertIfAbsent(user.id().value(), user.identityProviderSubject(), user.email(),
                user.displayName(), user.preferredLocale().code(), user.createdAt()) == 1;
    }

    @Override
    public void save(User user) {
        UserJpaEntity entity = jpa.findById(user.id().value())
                .orElseThrow(() -> new IllegalStateException("Unknown user " + user.id().value()));
        entity.update(user.email(), user.displayName(), user.preferredLocale().code(), user.updatedAt());
    }

    private static User toDomain(UserJpaEntity entity) {
        return User.restore(new UserId(entity.id()), entity.identityProviderSubject(), entity.email(),
                entity.displayName(), PreferredLocale.ofCode(entity.preferredLocale()).orElseThrow(),
                entity.createdAt(), entity.updatedAt());
    }
}
