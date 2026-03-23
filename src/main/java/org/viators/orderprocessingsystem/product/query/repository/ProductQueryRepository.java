package org.viators.orderprocessingsystem.product.query.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.viators.orderprocessingsystem.common.enums.CategoryEnum;
import org.viators.orderprocessingsystem.product.query.entity.ProductReadEntity;

import java.util.List;
import java.util.Optional;

public interface ProductQueryRepository extends JpaRepository<ProductReadEntity, Long> {

    Optional<ProductReadEntity> findByUuid(String uuid);

    List<ProductReadEntity> findByCategory(CategoryEnum category, Pageable pageable);

    @Query("""
            select pwe from ProductWriteEntity pwe
            """)
    Page<ProductReadEntity> findAllPaginated(Pageable pageable);
}
