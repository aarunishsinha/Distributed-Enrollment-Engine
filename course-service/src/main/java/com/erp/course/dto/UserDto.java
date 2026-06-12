package com.erp.course.dto;

import com.erp.course.model.User;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {

    private String userId;
    private String username;
    private String email;
    private String role;
    private LocalDateTime createdAt;

    public UserDto() {}

    public static UserDto fromEntity(User user) {
        UserDto dto = new UserDto();
        dto.userId = user.getUserId();
        dto.username = user.getUsername();
        dto.email = user.getEmail();
        dto.role = user.getRole();
        dto.createdAt = user.getCreatedAt();
        return dto;
    }

    // Getters and setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
