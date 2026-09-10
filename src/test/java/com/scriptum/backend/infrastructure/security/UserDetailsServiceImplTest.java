package com.scriptum.backend.infrastructure.security;

import com.scriptum.backend.infrastructure.database.jpa.UserJpaEntity;
import com.scriptum.backend.infrastructure.database.repository.IUserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsServiceImpl")
class UserDetailsServiceImplTest {

    @Mock
    private IUserJpaRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    private static final String EMAIL = "user@example.invalid";
    private static final String STORED_HASH = "hashed-password";

    private static UserJpaEntity user() {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        entity.setName("Original Name");
        entity.setEmail(EMAIL);
        entity.setPassword(STORED_HASH);
        entity.setEmailVerified(true);
        return entity;
    }

    @Test
    @DisplayName("loadUserByUsername maps the email to the username and carries the stored hash")
    void loadUserByUsernameMapsEmailAndHash() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));

        UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        assertThat(result.getUsername()).isEqualTo(EMAIL);
        assertThat(result.getPassword()).isEqualTo(STORED_HASH);
    }

    @Test
    @DisplayName("loadUserByUsername grants the single USER authority")
    void loadUserByUsernameGrantsUserAuthority() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));

        UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        assertThat(result.getAuthorities()).extracting(Object::toString).containsExactly("USER");
    }

    @Test
    @DisplayName("loadUserByUsername returns an enabled, unlocked account")
    void loadUserByUsernameReturnsUsableAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));

        UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        assertThat(result.isEnabled()).isTrue();
        assertThat(result.isAccountNonLocked()).isTrue();
        assertThat(result.isAccountNonExpired()).isTrue();
        assertThat(result.isCredentialsNonExpired()).isTrue();
    }

    @Test
    @DisplayName("loadUserByUsername throws UsernameNotFoundException naming the email when unknown")
    void loadUserByUsernameThrowsForUnknownEmail() {
        when(userRepository.findByEmail("ghost@example.invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost@example.invalid"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost@example.invalid");
    }

    @Test
    @DisplayName("loadUserByUsername does not gate on the email-verified flag")
    void loadUserByUsernameIgnoresEmailVerifiedFlag() {
        UserJpaEntity unverified = user();
        unverified.setEmailVerified(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverified));

        UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        assertThat(result.isEnabled()).isTrue();
    }
}
