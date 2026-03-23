package org.viators.orderprocessingsystem.user.command.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.viators.orderprocessingsystem.user.command.entity.UserWriteEntity;

import java.util.Optional;

public interface UserCommandRepository extends JpaRepository<UserWriteEntity, Long> {

    Optional<UserWriteEntity> findByUuid(String userUuid);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
