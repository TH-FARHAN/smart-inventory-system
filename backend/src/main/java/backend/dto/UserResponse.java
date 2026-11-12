package backend.dto;

import backend.entity.User;

public record UserResponse(Long id, String name, String email, String role, boolean active) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isActive());
    }
}