package com.barberstudio.client;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByIdAndBarbershopId(Long id, Long barbershopId);

    Optional<Client> findByBarbershopIdAndWhatsappE164AndArchivedAtIsNull(Long barbershopId, String whatsappE164);

    @Query("select c from Client c where c.barbershopId = :shop and c.archivedAt is null order by c.name")
    List<Client> findActive(@Param("shop") Long shop, Pageable pageable);

    @Query("select c from Client c where c.barbershopId = :shop and c.archivedAt is null "
         + "and (lower(c.name) like :name or c.whatsappE164 like :phone) order by c.name")
    List<Client> search(@Param("shop") Long shop, @Param("name") String name, @Param("phone") String phone, Pageable pageable);

    List<Client> findTop5ByBarbershopIdAndArchivedAtIsNullOrderByCreatedAtDesc(Long barbershopId);
}
