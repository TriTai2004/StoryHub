package story.config;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import story.feature.user.model.User;

public record CustomUserDetails(
        UUID id,
        String email,
        String password,
        User.Status status,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }
    @Override
    public boolean isEnabled() {
        return status == User.Status.ACTIVE;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != User.Status.BANNED;
    }
}
