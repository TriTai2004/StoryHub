package story.feature.auth.dto.req;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must not exceed 100 characters")
    String displayName,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters")
    String password
) {
    @JsonIgnore
    @AssertTrue(message = "Password must not exceed 72 bytes in UTF-8")
    public boolean isPasswordWithinByteLimit() {
        // BCrypt limits input by bytes, so character count alone is insufficient.
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
