package org.vivek.appointmentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vivek.appointmentservice.entity.CachedPatient;

import java.util.UUID;

public interface CachedPatientRepository extends JpaRepository<CachedPatient, UUID> {
}
