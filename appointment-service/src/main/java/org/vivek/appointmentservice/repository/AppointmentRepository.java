package org.vivek.appointmentservice.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.vivek.appointmentservice.entity.Appointment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    List<Appointment> findByStartTimeBetween(LocalDateTime from, LocalDateTime to);
}
