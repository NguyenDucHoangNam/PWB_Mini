package com.pwb.backend.repository.rdbms;

import com.pwb.backend.entity.rdbms.User;
import com.pwb.backend.repository.BaseRepository;

import java.util.Optional;

public interface UserRepository extends BaseRepository<User> {

    Optional<User> findByEmailAndDeletedFalse(String email);

    Optional<User> findByUsernameAndDeletedFalse(String username);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByUsernameAndDeletedFalse(String username);
}
