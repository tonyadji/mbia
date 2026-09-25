package com.lehnade.mbia.identity.domain;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findBySubject(String identityProviderSubject);

    Optional<User> findById(UserId id);

    /**
     * Inserts the user unless one with the same subject or the same active email already exists,
     * including one inserted concurrently by another transaction.
     *
     * @return whether the user was inserted
     */
    boolean insertIfAbsent(User user);

    /** Stores the changes of an existing user. */
    void save(User user);
}
