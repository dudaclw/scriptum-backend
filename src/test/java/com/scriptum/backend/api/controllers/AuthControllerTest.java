package com.scriptum.backend.api.controllers;

import com.scriptum.backend.domain.request.AuthRequestBody;
import com.scriptum.backend.domain.request.UserRequestBody;
import com.scriptum.backend.infrastructure.database.jpa.UserJpaEntity;
import com.scriptum.backend.infrastructure.security.JwtService;
import com.scriptum.backend.service.UserService;
import com.scriptum.backend.service.VerificationTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebLayerTest(AuthController.class)
@DisplayName("AuthController")
class AuthControllerTest extends WebLayerTestSupport {

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private VerificationTokenService verificationTokenService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EMAIL = "user@example.invalid";
    private static final String TOKEN = "a-signed-token";

    private static UserJpaEntity user() {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(USER_ID);
        entity.setName("Original Name");
        entity.setEmail(EMAIL);
        entity.setPassword("hashed-password");
        entity.setEmailVerified(false);
        entity.setNewUser(true);
        return entity;
    }

    private static Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(EMAIL, "plain-password", List.of());
    }

    @Test
    @DisplayName("GET /api/auth/health returns 200 with a plain-text banner")
    void healthCheckReturnsOk() throws Exception {
        mockMvc.perform(get("/api/auth/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("API running..."));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 201 with the issued token")
    void registerReturnsCreated() throws Exception {
        UserRequestBody request = new UserRequestBody("Original Name", EMAIL, "secret123", null);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");
        when(userService.save(any(UserJpaEntity.class))).thenReturn(user());
        when(jwtService.generateToken(EMAIL)).thenReturn(TOKEN);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.token").value(TOKEN))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.newUser").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/register stores a hash, never the plain password")
    void registerStoresHashedPassword() throws Exception {
        UserRequestBody request = new UserRequestBody("Original Name", EMAIL, "secret123", null);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");
        when(userService.save(any(UserJpaEntity.class))).thenReturn(user());
        when(jwtService.generateToken(EMAIL)).thenReturn(TOKEN);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(passwordEncoder).encode("secret123");
    }

    @Test
    @DisplayName("POST /api/auth/register sends the verification email")
    void registerSendsVerificationEmail() throws Exception {
        UserRequestBody request = new UserRequestBody("Original Name", EMAIL, "secret123", null);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");
        when(userService.save(any(UserJpaEntity.class))).thenReturn(user());
        when(jwtService.generateToken(EMAIL)).thenReturn(TOKEN);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(verificationTokenService).createVerificationToken(any(UserJpaEntity.class));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 409 when the email is already registered")
    void registerReturnsConflictForExistingEmail() throws Exception {
        UserRequestBody request = new UserRequestBody("Original Name", EMAIL, "secret123", null);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user()));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        verify(userService, never()).save(any());
        verify(verificationTokenService, never()).createVerificationToken(any());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 when the email is not well formed")
    void registerRejectsMalformedEmail() throws Exception {
        UserRequestBody request = new UserRequestBody("Original Name", "not-an-email", "secret123", null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").value("email"));

        verify(userService, never()).save(any());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 when the password is too short")
    void registerRejectsShortPassword() throws Exception {
        UserRequestBody request = new UserRequestBody("Original Name", EMAIL, "abc", null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").value("password"));

        verify(userService, never()).save(any());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 with the issued token")
    void loginReturnsOk() throws Exception {
        AuthRequestBody request = new AuthRequestBody(EMAIL, "secret123");
        when(authenticationManager.authenticate(any())).thenReturn(authentication());
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
        when(jwtService.generateToken(EMAIL)).thenReturn(TOKEN);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(TOKEN))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 with no body for wrong credentials")
    void loginReturnsUnauthorizedForBadCredentials() throws Exception {
        AuthRequestBody request = new AuthRequestBody(EMAIL, "wrong-password");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));

        verify(jwtService, never()).generateToken(anyString());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 when the password is absent")
    void loginRejectsMissingPassword() throws Exception {
        AuthRequestBody request = new AuthRequestBody(EMAIL, "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").value("password"));

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("GET /api/auth/verify-email returns 200 for a token that verifies")
    void verifyEmailReturnsOkForValidToken() throws Exception {
        when(verificationTokenService.verifyEmail("valid-token")).thenReturn(true);

        mockMvc.perform(get("/api/auth/verify-email").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email verified successfully"));
    }

    @Test
    @DisplayName("GET /api/auth/verify-email returns 400 for a token that does not verify")
    void verifyEmailReturnsBadRequestForInvalidToken() throws Exception {
        when(verificationTokenService.verifyEmail("stale-token")).thenReturn(false);

        mockMvc.perform(get("/api/auth/verify-email").param("token", "stale-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    @DisplayName("GET /api/auth/verify-email returns 400 when the token parameter is missing")
    void verifyEmailRequiresToken() throws Exception {
        mockMvc.perform(get("/api/auth/verify-email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/resend-verification returns 200 when the email is queued")
    void resendVerificationReturnsOk() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification").param("userId", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Verification email sent successfully"));

        verify(verificationTokenService).resendVerificationToken(USER_ID);
    }

    @Test
    @DisplayName("POST /api/auth/resend-verification returns 500 when sending fails")
    void resendVerificationReturnsServerErrorOnFailure() throws Exception {
        doThrow(new IllegalArgumentException("User not found with id: " + USER_ID))
                .when(verificationTokenService).resendVerificationToken(USER_ID);

        mockMvc.perform(post("/api/auth/resend-verification").param("userId", USER_ID.toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/resend-verification returns 400 when userId is not a UUID")
    void resendVerificationRejectsMalformedUserId() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification").param("userId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
