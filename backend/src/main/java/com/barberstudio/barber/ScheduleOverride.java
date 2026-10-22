package com.barberstudio.barber;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schedule_override")
@Getter @Setter @NoArgsConstructor
public class ScheduleOverride extends BaseEntity {
    @Column(name = "barber_id") private Long barberId;
    @Column(name = "work_date") private LocalDate workDate;
    @Column(name = "day_off") private boolean dayOff;
    @Column(name = "start_time") private LocalTime startTime;
    @Column(name = "end_time") private LocalTime endTime;
    @Column(name = "break_start") private LocalTime breakStart;
    @Column(name = "break_end") private LocalTime breakEnd;
    private String note;
}
