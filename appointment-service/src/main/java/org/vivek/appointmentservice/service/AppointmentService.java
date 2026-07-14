package org.vivek.appointmentservice.service;

import org.springframework.stereotype.Service;
import org.vivek.appointmentservice.dto.AppointmentResponseDto;
import org.vivek.appointmentservice.entity.CachedPatient;
import org.vivek.appointmentservice.repository.AppointmentRepository;
import org.vivek.appointmentservice.repository.CachedPatientRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final CachedPatientRepository cachedPatientRepository;

    public AppointmentService(AppointmentRepository appointmentRepository, CachedPatientRepository cachedPatientRepository) {
        this.appointmentRepository = appointmentRepository;
        this.cachedPatientRepository = cachedPatientRepository;
    }

    public List<AppointmentResponseDto> getAppointmentsByTimeRange(LocalDateTime from, LocalDateTime to) {
        return appointmentRepository.findByStartTimeBetween(from, to).stream()
                .map(appointment -> {

                    String name = cachedPatientRepository
                            .findById(appointment.getPatientId())
                            .map(CachedPatient::getFullName)
                            .orElse("Unknown");

                    AppointmentResponseDto appointmentResponseDto = new AppointmentResponseDto();

                    appointmentResponseDto.setId(appointment.getId());
                    appointmentResponseDto.setPatientId(appointment.getPatientId());
                    appointmentResponseDto.setStartTime(appointment.getStartTime());
                    appointmentResponseDto.setEndTime(appointment.getEndTime());
                    appointmentResponseDto.setReason(appointment.getReason());
                    appointmentResponseDto.setVersion(appointment.getVersion());
                    appointmentResponseDto.setPatientName(name);

                    return appointmentResponseDto;
                }).toList();
    }
}
