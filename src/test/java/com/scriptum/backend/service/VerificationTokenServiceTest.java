package com.scriptum.backend.service;

import com.scriptum.backend.infrastructure.database.jpa.UserJpaEntity;
import com.scriptum.backend.infrastructure.database.jpa.VerificationToken;
import com.scriptum.backend.infrastructure.database.repository.IVerificationTokenRepository;
import com.scriptum.backend.infrastructure.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationTokenService")
class VerificationTokenServiceTest {

    @Mock
    private IVerificationTokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private UserService userService;

    @InjectMocks
    private VerificationTokenService verificationTokenService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EMAIL = "user@example.invalid";
    private static final int EXPIRY_MINUTES = 1440;

    @BeforeEach
    void injectExpiryWindow() {
        // @Value is not processed outside a Spring context, so the field is set directly.
        ReflectionTestUtils.setField(verificationTokenService, "tokenExpiryMinutes", EXPIRY_MINUTES);
    }

    private static UserJpaEntity user() {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(USER_ID);
        entity.setName("Original Name");
        entity.setEmail(EMAIL);
        entity.setPassword("hashed-password");
        entity.setEmailVerified(false);
        return entity;
    }

    @Test
    @DisplayName("createVerificationToken deletes the previous unverified token for the same user")
    void createVerificationTokenDeletesPreviousUnverifiedToken() {
        VerificationToken previous = VerificationToken.builder()
                .token("previous-token")
                .user(user())
                .expiryDate(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES))
                .verified(false)
                .build();
        when(tokenRepository.findByUserIdAndVerifiedFalse(USER_ID)).thenReturn(Optional.of(previous));

        verificationTokenService.createVerificationToken(user());

        verify(tokenRepository).delete(previous);
        verify(tokenRepository).save(any(VerificationToken.class));
    }

    @Test
    @DisplayName("createVerificationToken deletes nothing when the user has no pending token")
    void createVerificationTokenDeletesNothingWhenNoPendingToken() {
        when(tokenRepository.findByUserIdAndVerifiedFalse(USER_ID)).thenReturn(Optional.empty());

        verificationTokenService.createVerificationToken(user());

        verify(tokenRepository, never()).delete(any());
        verify(tokenRepository).save(any(VerificationToken.class));
    }

    @Test
    @DisplayName("createVerificationToken stores an unverified token bound to the user with a future expiry")
    void createVerificationTokenStoresUnverifiedTokenWithFutureExpiry() {
        UserJpaEntity owner = user();
        when(tokenRepository.findByUserIdAndVerifiedFalse(USER_ID)).thenReturn(Optional.empty());
        LocalDateTime before = LocalDateTime.now();

        verificationTokenService.createVerificationToken(owner);

        ArgumentCaptor<VerificationToken> saved = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository).save(saved.capture());
        VerificationToken token = saved.getValue();

        assertThat(token.isVerified()).isFalse();
        assertThat(token.getUser()).isSameAs(owner);
        assertThat(token.getToken()).isNotBlank();
        assertThat(UUID.fromString(token.getToken())).isNotNull();
        assertThat(token.getExpiryDate())
                .isAfterOrEqualTo(before.plusMinutes(EXPIRY_MINUTES))
                .isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    @DisplayName("createVerificationToken emails the generated token to the user address")
    void createVerificationTokenEmailsGeneratedToken() {
        when(tokenRepository.findByUserIdAndVerifiedFalse(USER_ID)).thenReturn(Optional.empty());

        verificationTokenService.createVerificationToken(user());

        ArgumentCaptor<VerificationToken> saved = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository).save(saved.capture());
        verify(emailService).sendVerificationEmail(EMAIL, saved.getValue().getToken());
    }

    @Test
    @DisplayName("verifyEmail returns false and changes nothing for an unknown token")
    void verifyEmailReturnsFalseForUnknownToken() {
        when(tokenRepository.findByToken("ghost-token")).thenReturn(Optional.empty());

        assertThat(verificationTokenService.verifyEmail("ghost-token")).isFalse();

        verify(tokenRepository, never()).save(any());
        verify(userService, never()).save(any());
    }

    @Test
    @DisplayName("verifyEmail returns false and changes nothing for an expired token")
    void verifyEmailReturnsFalseForExpiredToken() {
        UserJpaEntity owner = user();
        VerificationToken expired = VerificationToken.builder()
                .token("expired-token")
                .user(owner)
                .expiryDate(LocalDateTime.now().minusMinutes(1))
                .verified(false)
                .build();
        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThat(verificationTokenService.verifyEmail("expired-token")).isFalse();

        assertThat(expired.isVerified()).isFalse();
        assertThat(owner.isEmailVerified()).isFalse();
        verify(tokenRepository, never()).save(any());
        verify(userService, never()).save(any());
    }

    @Test
    @DisplayName("verifyEmail marks both the token and the user as verified for a valid token")
    void verifyEmailMarksTokenAndUserVerified() {
        UserJpaEntity owner = user();
        VerificationToken valid = VerificationToken.builder()
                .token("valid-token")
                .user(owner)
                .expiryDate(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES))
                .verified(false)
                .build();
        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(valid));

        assertThat(verificationTokenService.verifyEmail("valid-token")).isTrue();

        assertThat(valid.isVerified()).isTrue();
        assertThat(owner.isEmailVerified()).isTrue();
        verify(tokenRepository).save(valid);
        verify(userService).save(owner);
    }

    @Test
    @DisplayName("resendVerificationToken resolves the user then issues a fresh token")
    void resendVerificationTokenIssuesFreshToken() {
        UserJpaEntity owner = user();
        when(userService.findByIdOrElseThrow(USER_ID)).thenReturn(owner);
        when(tokenRepository.findByUserIdAndVerifiedFalse(USER_ID)).thenReturn(Optional.empty());

        verificationTokenService.resendVerificationToken(USER_ID);

        verify(userService).findByIdOrElseThrow(USER_ID);
        verify(tokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("resendVerificationToken propagates the lookup failure for an unknown user")
    void resendVerificationTokenPropagatesUnknownUser() {
        when(userService.findByIdOrElseThrow(USER_ID))
                .thenThrow(new IllegalArgumentException("User not found with id: " + USER_ID));

        assertThatThrownBy(() -> verificationTokenService.resendVerificationToken(USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(USER_ID.toString());

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }
}
