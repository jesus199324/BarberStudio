package com.barberstudio.barber;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {
    List<WorkingHours> findByBarberIdOrderByWeekday(Long barberId);
    List<WorkingHours> findByBarberIdIn(Collection<Long> barberIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorkingHours w where w.barberId = :id")
    void deleteAllFor(@Param("id") Long barberId);
}
