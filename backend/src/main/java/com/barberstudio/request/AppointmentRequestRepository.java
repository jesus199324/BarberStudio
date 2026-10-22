package com.barberstudio.request;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRequestRepository extends JpaRepository<AppointmentRequest, Long> {
    Optional<AppointmentRequest> findByIdAndBarbershopId(Long id, Long barbershopId);
    List<AppointmentRequest> findByBarbershopIdOrderByCreatedAtDesc(Long barbershopId);
    List<AppointmentRequest> findByBarbershopIdAndStatusOrderByCreatedAtDesc(Long barbershopId, RequestStatus status);
    long countByBarbershopIdAndStatus(Long barbershopId, RequestStatus status);
    long countByBarbershopIdAndWhatsappE164AndStatusAndCreatedAtAfter(Long shop, String phone, RequestStatus status, Instant after);
}
