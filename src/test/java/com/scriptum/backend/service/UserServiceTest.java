package com.scriptum.backend.service;

import com.scriptum.backend.domain.request.UserRequestBody;
import com.scriptum.backend.domain.response.UserResponseBody;
import com.scriptum.backend.infrastructure.database.jpa.UserJpaEntity;
import com.scriptum.backend.infrastructure.database.repository.IUserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private IUserJpaRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EMAIL = "user@example.invalid";
    private static final String STORED_HASH = "hashed-old-password";

    private static UserJpaEntity user() {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(USER_ID);
        entity.setName("Original Name");
        entity.setEmail(EMAIL);
        entity.setPassword(STORED_HASH);
        entity.setAvatarUrl("https://example.invalid/avatar.png");
        entity.setEmailVerified(true);
        entity.setNewUser(false);
        return entity;
    }

    @Test
    @DisplayName("findByEmail delegates to the repository")
    void findByEmailDelegatesToRepository() {
        UserJpaEntity stored = user();
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(stored));

        assertThat(userService.findByEmail(EMAIL)).containsSame(stored);
        verify(repository).findByEmail(EMAIL);
    }

    @Test
    @DisplayName("findByIdOrElseThrow returns the user when it exists")
    void findByIdOrElseThrowReturnsExistingUser() {
        UserJpaEntity stored = user();
        when(repository.findById(USER_ID)).thenReturn(Optional.of(stored));

        assertThat(userService.findByIdOrElseThrow(USER_ID)).isSameAs(stored);
    }

    @Test
    @DisplayName("findByIdOrElseThrow throws IllegalArgumentException naming the id when missing")
    void findByIdOrElseThrowThrowsWhenMissing() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByIdOrElseThrow(USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(USER_ID.toString());
    }

    @Test
    @DisplayName("save delegates to the repository")
    void saveDelegatesToRepository() {
        UserJpaEntity stored = user();
        when(repository.save(stored)).thenReturn(stored);

        assertThat(userService.save(stored)).isSameAs(stored);
        verify(repository).save(stored);
    }

    @Test
    @DisplayName("mapToResponseBody copies the public fields and never exposes the password")
    void mapToResponseBodyNeverExposesPassword() {
        UserResponseBody result = userService.mapToResponseBody(user());

        assertThat(result.getId()).isEqualTo(USER_ID);
        assertThat(result.getName()).isEqualTo("Original Name");
        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.getAvatarUrl()).isEqualTo("https://example.invalid/avatar.png");
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.toString()).doesNotContain(STORED_HASH);
    }

    @Test
    @DisplayName("getUserResponseById maps the stored user to a response body")
    void getUserResponseByIdMapsStoredUser() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user()));

        assertThat(userService.getUserResponseById(USER_ID).getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("getCurrentUserResponse resolves the user from the authenticated email")
    void getCurrentUserResponseResolvesByEmail() {
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));

        assertThat(userService.getCurrentUserResponse(EMAIL).getId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("getCurrentUserResponse throws IllegalArgumentException when the email is unknown")
    void getCurrentUserResponseThrowsWhenEmailUnknown() {
        when(repository.findByEmail("ghost@example.invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUserResponse("ghost@example.invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("updateUser overwrites name and email and returns the updated response body")
    void updateUserOverwritesNameAndEmail() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(repository.findByEmail("new@example.invalid")).thenReturn(Optional.empty());
        when(repository.save(any(UserJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        UserRequestBody request = new UserRequestBody("New Name", "new@example.invalid", null, null);
        UserResponseBody result = userService.updateUser(USER_ID, request);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getEmail()).isEqualTo("new@example.invalid");
        assertThat(result.getId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("updateUser rejects an email already taken by another user and saves nothing")
    void updateUserRejectsEmailAlreadyInUse() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(repository.findByEmail("taken@example.invalid")).thenReturn(Optional.of(new UserJpaEntity()));

        UserRequestBody request = new UserRequestBody("New Name", "taken@example.invalid", null, null);

        assertThatThrownBy(() -> userService.updateUser(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("updateUser skips the uniqueness lookup when the email is unchanged")
    void updateUserSkipsUniquenessLookupWhenEmailUnchanged() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(repository.save(any(UserJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        userService.updateUser(USER_ID, new UserRequestBody("New Name", EMAIL, null, null));

        verify(repository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("updateUser encodes the password when a new one is supplied")
    void updateUserEncodesSuppliedPassword() {
        UserJpaEntity stored = user();
        when(repository.findById(USER_ID)).thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("new-plain-password")).thenReturn("hashed-new-password");
        when(repository.save(any(UserJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        UserRequestBody request = new UserRequestBody("Original Name", EMAIL, "new-plain-password", null);
        userService.updateUser(USER_ID, request);

        assertThat(stored.getPassword()).isEqualTo("hashed-new-password");
        verify(passwordEncoder).encode("new-plain-password");
    }

    @Test
    @DisplayName("updateUser keeps the stored hash when the password is null")
    void updateUserKeepsHashWhenPasswordIsNull() {
        UserJpaEntity stored = user();
        when(repository.findById(USER_ID)).thenReturn(Optional.of(stored));
        when(repository.save(any(UserJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        userService.updateUser(USER_ID, new UserRequestBody("Original Name", EMAIL, null, null));

        assertThat(stored.getPassword()).isEqualTo(STORED_HASH);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("updateUser keeps the stored hash when the password is blank")
    void updateUserKeepsHashWhenPasswordIsBlank() {
        UserJpaEntity stored = user();
        when(repository.findById(USER_ID)).thenReturn(Optional.of(stored));
        when(repository.save(any(UserJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        userService.updateUser(USER_ID, new UserRequestBody("Original Name", EMAIL, "", null));

        assertThat(stored.getPassword()).isEqualTo(STORED_HASH);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("updateUser replaces the avatar url when one is supplied")
    void updateUserReplacesAvatarWhenSupplied() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(repository.save(any(UserJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        UserRequestBody request = new UserRequestBody(
                "Original Name", EMAIL, null, "https://example.invalid/new-avatar.png");
        UserResponseBody result = userService.updateUser(USER_ID, request);

        assertThat(result.getAvatarUrl()).isEqualTo("https://example.invalid/new-avatar.png");
    }

    @Test
    @DisplayName("updateUser keeps the stored avatar url when none is supplied")
    void updateUserKeepsAvatarWhenAbsent() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(repository.save(any(UserJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        UserResponseBody result = userService.updateUser(
                USER_ID, new UserRequestBody("Original Name", EMAIL, null, null));

        assertThat(result.getAvatarUrl()).isEqualTo("https://example.invalid/avatar.png");
    }

    @Test
    @DisplayName("updateUser throws IllegalArgumentException when the user does not exist")
    void updateUserThrowsWhenUserMissing() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(
                USER_ID, new UserRequestBody("New Name", EMAIL, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }
}
