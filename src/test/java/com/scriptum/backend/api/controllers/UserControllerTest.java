package com.scriptum.backend.api.controllers;

import com.scriptum.backend.domain.request.UserUpdateRequestBody;
import com.scriptum.backend.domain.response.UserResponseBody;
import com.scriptum.backend.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebLayerTest(UserController.class)
@DisplayName("UserController")
class UserControllerTest extends WebLayerTestSupport {

    @MockitoBean
    private UserService userService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EMAIL = "user@example.invalid";

    private static UserUpdateRequestBody updateRequest(
            String name, String email, String password, String avatarUrl) {
        UserUpdateRequestBody request = new UserUpdateRequestBody();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        request.setAvatarUrl(avatarUrl);
        return request;
    }

    private static UserResponseBody response() {
        UserResponseBody body = new UserResponseBody();
        body.setId(USER_ID);
        body.setName("Original Name");
        body.setEmail(EMAIL);
        body.setAvatarUrl("https://example.invalid/avatar.png");
        body.setEmailVerified(true);
        return body;
    }

    @Test
    @DisplayName("GET /api/users/me returns 200 with the authenticated user")
    @WithMockUser(username = EMAIL)
    void getCurrentUserReturnsOk() throws Exception {
        when(userService.getCurrentUserResponse(EMAIL)).thenReturn(response());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    @DisplayName("GET /api/users/me never serialises a password field")
    @WithMockUser(username = EMAIL)
    void getCurrentUserOmitsPassword() throws Exception {
        when(userService.getCurrentUserResponse(EMAIL)).thenReturn(response());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/users/me returns 401 when there is no authentication")
    void getCurrentUserRejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getCurrentUserResponse(any());
    }

    @Test
    @DisplayName("GET /api/users/me resolves the user from the authenticated principal, not a parameter")
    @WithMockUser(username = "someone.else@example.invalid")
    void getCurrentUserUsesPrincipalNotParameter() throws Exception {
        when(userService.getCurrentUserResponse("someone.else@example.invalid")).thenReturn(response());

        mockMvc.perform(get("/api/users/me").param("email", EMAIL))
                .andExpect(status().isOk());

        verify(userService).getCurrentUserResponse("someone.else@example.invalid");
    }

    @Test
    @DisplayName("GET /api/users/{id} returns 200 with the user")
    void getUserByIdReturnsOk() throws Exception {
        when(userService.getUserResponseById(USER_ID)).thenReturn(response());

        mockMvc.perform(get("/api/users/{id}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Original Name"));
    }

    @Test
    @DisplayName("GET /api/users/{id} returns 400 when the id is not a UUID")
    void getUserByIdRejectsMalformedId() throws Exception {
        mockMvc.perform(get("/api/users/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/users/{id} returns 200 with the updated user")
    void updateUserReturnsOk() throws Exception {
        UserUpdateRequestBody request = updateRequest("New Name", EMAIL, "secret123", null);
        when(userService.updateUser(eq(USER_ID), any(UserUpdateRequestBody.class))).thenReturn(response());

        mockMvc.perform(put("/api/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    @Test
    @DisplayName("PUT /api/users/{id} returns 400 when the email is not well formed")
    void updateUserRejectsMalformedEmail() throws Exception {
        UserUpdateRequestBody request = updateRequest("New Name", "not-an-email", "secret123", null);

        mockMvc.perform(put("/api/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").value("email"))
                .andExpect(jsonPath("$.fieldsMessage").value("Email should be valid"));
    }

    @Test
    @DisplayName("PUT /api/users/{id} returns 400 when the password is shorter than six characters")
    void updateUserRejectsShortPassword() throws Exception {
        UserUpdateRequestBody request = updateRequest("New Name", EMAIL, "abc", null);

        mockMvc.perform(put("/api/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").value("password"))
                .andExpect(jsonPath("$.fieldsMessage").value("Password must be at least 8 characters"));
    }

    @Test
    @DisplayName("PUT /api/users/{id} returns 400 when the name is blank")
    void updateUserRejectsBlankName() throws Exception {
        UserUpdateRequestBody request = updateRequest("   ", EMAIL, "secret123", null);

        mockMvc.perform(put("/api/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").value("name"));
    }
}
