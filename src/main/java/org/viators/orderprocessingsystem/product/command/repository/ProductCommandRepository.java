package org.viators.orderprocessingsystem.product.command.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.viators.orderprocessingsystem.product.command.entity.ProductWriteEntity;

import java.util.Optional;

@Repository
public interface ProductCommandRepository extends JpaRepository<ProductWriteEntity, Long> {

    Optional<ProductWriteEntity> findByUuid(String uuid);

    boolean existsByNameIgnoreCase(String name);
}
