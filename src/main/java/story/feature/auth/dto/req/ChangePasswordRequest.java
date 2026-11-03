package story.feature.auth.dto.req;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "Current password is required")
    String currentPassword,

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 72, message = "New password must contain between 8 and 72 characters")
    String newPassword,

    @NotBlank(message = "Password confirmation is required")
    String confirmNewPassword
) {
    @JsonIgnore
    @AssertTrue(message = "Current password must not exceed 72 bytes in UTF-8")
    public boolean isCurrentPasswordWithinByteLimit() {
        return withinBcryptLimit(currentPassword);
    }

    @JsonIgnore
    @AssertTrue(message = "New password must not exceed 72 bytes in UTF-8")
    public boolean isNewPasswordWithinByteLimit() {
        return withinBcryptLimit(newPassword);
    }

    @JsonIgnore
    @AssertTrue(message = "Password confirmation does not match")
    public boolean isNewPasswordConfirmed() {
        return newPassword == null || newPassword.equals(confirmNewPassword);
    }

    private static boolean withinBcryptLimit(String password) {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
