package org.vivek.appointmentservice.service;

import org.springframework.stereotype.Service;
import org.vivek.appointmentservice.dto.AppointmentResponseDto;
import org.vivek.appointmentservice.repository.AppointmentRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    public List<AppointmentResponseDto> getAppointmentsByTimeRange(LocalDateTime from, LocalDateTime to) {
        return appointmentRepository.findByStartTimeBetween(from, to).stream()
                .map(appointment -> {AppointmentResponseDto appointmentResponseDto = new AppointmentResponseDto();

                appointmentResponseDto.setId(appointment.getId());
                appointmentResponseDto.setPatientId(appointment.getPatientId());
              //  appointmentResponseDto.setPatientName(name);
                appointmentResponseDto.setStartTime(appointment.getStartTime());
                appointmentResponseDto.setEndTime(appointment.getEndTime());
                appointmentResponseDto.setReason(appointment.getReason());
                appointmentResponseDto.setVersion(appointment.getVersion());

                return appointmentResponseDto;
                }).toList();
    }
}
