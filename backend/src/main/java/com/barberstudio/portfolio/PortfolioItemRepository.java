package com.barberstudio.portfolio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {
    Optional<PortfolioItem> findByIdAndBarbershopId(Long id, Long barbershopId);

    @EntityGraph(attributePaths = "style")
    Optional<PortfolioItem> findByIdAndBarbershopIdAndStatus(Long id, Long barbershopId, PortfolioStatus status);

    @EntityGraph(attributePaths = "style")
    List<PortfolioItem> findByBarbershopIdOrderByCreatedAtDesc(Long barbershopId);

    @EntityGraph(attributePaths = "style")
    List<PortfolioItem> findByBarbershopIdAndStatusOrderByCreatedAtDesc(Long barbershopId, PortfolioStatus status);

    /** Galeria publica: el filtro status = PUBLISHED vive aqui, en el servidor, nunca en el frontend. */
    @EntityGraph(attributePaths = "style")
    @Query("select i from PortfolioItem i where i.barbershopId = :shop "
         + "and i.status = com.barberstudio.portfolio.PortfolioStatus.PUBLISHED "
         + "and (:styleId = 0L or i.style.id = :styleId) and lower(i.title) like :q")
    Page<PortfolioItem> searchPublished(@Param("shop") Long shop, @Param("styleId") Long styleId,
                                        @Param("q") String q, Pageable pageable);
}
