package com.erp.course.service;

import com.erp.course.dto.UserDto;
import com.erp.course.model.User;
import com.erp.course.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<UserDto> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserDto::fromEntity);
    }

    public Optional<UserDto> getUserById(String userId) {
        return userRepository.findById(userId).map(UserDto::fromEntity);
    }

    public UserDto addUser(UserDto dto) {
        if (userRepository.existsById(dto.getUserId())) {
            throw new IllegalArgumentException("User with ID " + dto.getUserId() + " already exists");
        }

        User user = new User();
        user.setUserId(dto.getUserId());
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole() != null ? dto.getRole() : "STUDENT");

        User saved = userRepository.save(user);
        log.info("User created: {} (role={})", saved.getUserId(), saved.getRole());
        return UserDto.fromEntity(saved);
    }

    public boolean deleteUser(String userId) {
        if (!userRepository.existsById(userId)) return false;
        userRepository.deleteById(userId);
        log.info("User deleted: {}", userId);
        return true;
    }
}
