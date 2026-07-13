package org.vivek.appointmentservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.vivek.appointmentservice.dto.AppointmentResponseDto;
import org.vivek.appointmentservice.service.AppointmentService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<AppointmentResponseDto> getAppointmentsByTimeRange(@RequestParam LocalDateTime from, @RequestParam LocalDateTime to) {
        return appointmentService.getAppointmentsByTimeRange(from, to);
    }
}
