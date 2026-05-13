package com.pms.patientmanagementsystem.service;

import com.pms.patientmanagementsystem.dto.PatientRequestDTO;
import com.pms.patientmanagementsystem.dto.PatientResponseDTO;
import com.pms.patientmanagementsystem.exception.EmailAlreadyExistsException;
import com.pms.patientmanagementsystem.mapper.PatientMapper;
import com.pms.patientmanagementsystem.model.Patient;
import com.pms.patientmanagementsystem.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PatientService {
    private PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public List<PatientResponseDTO> getPatients() {
        List<Patient> patients = patientRepository.findAll();

        return patients.stream()
                .map(PatientMapper::toDTO).toList();
    }

    public PatientResponseDTO createPatient(PatientRequestDTO patientRequestDTO) {
        Patient newPatient = patientRepository.save(PatientMapper.toModel(patientRequestDTO));

        if (patientRepository.existsByEmail(patientRequestDTO.getEmail())) {
            throw new EmailAlreadyExistsException("A patient with this email already exists : " + patientRequestDTO.getEmail());
        }

        // An email address must be unique
        return PatientMapper.toDTO(newPatient);
    }
}
