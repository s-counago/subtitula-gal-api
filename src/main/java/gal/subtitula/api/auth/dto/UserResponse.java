package gal.subtitula.api.auth.dto;

import gal.subtitula.api.user.User;
import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, boolean emailVerified) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.isEmailVerified());
    }
}
