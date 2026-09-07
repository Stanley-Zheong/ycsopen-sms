package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    /** Serializes concurrent login-failure increments for one account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);
}
