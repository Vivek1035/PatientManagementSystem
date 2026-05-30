package org.vivek.authservice.service;

import org.springframework.stereotype.Service;
import org.vivek.authservice.dto.LoginRequestDTO;
import org.vivek.authservice.dto.LoginResponseDTO;
import org.vivek.authservice.model.User;
import org.vivek.authservice.repository.UserRepository;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
