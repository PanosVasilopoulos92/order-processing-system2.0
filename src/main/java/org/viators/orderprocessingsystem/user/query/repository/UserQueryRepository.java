package org.viators.orderprocessingsystem.user.query.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.viators.orderprocessingsystem.user.query.entity.UserReadEntity;

import java.util.Optional;

public interface UserQueryRepository extends JpaRepository<UserReadEntity, Long>,
    JpaSpecificationExecutor<UserReadEntity> {

    Optional<UserReadEntity> findByUuid(String uuid);

}

