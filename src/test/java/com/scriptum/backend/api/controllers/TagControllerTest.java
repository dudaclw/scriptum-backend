package com.scriptum.backend.api.controllers;

import com.scriptum.backend.configuration.exception.BadRequestException;
import com.scriptum.backend.domain.request.TagRequestBody;
import com.scriptum.backend.domain.response.TagResponseBody;
import com.scriptum.backend.service.TagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebLayerTest(TagController.class)
@DisplayName("TagController")
class TagControllerTest extends WebLayerTestSupport {

    @MockitoBean
    private TagService tagService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TAG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static TagResponseBody response() {
        TagResponseBody body = new TagResponseBody();
        body.setId(TAG_ID);
        body.setName("java");
        body.setColor("#00FF00");
        body.setUserId(USER_ID);
        body.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        body.setModifiedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        return body;
    }

    @Test
    @DisplayName("GET /api/tags returns 200 with the caller's tags")
    void getAllTagsReturnsOk() throws Exception {
        when(tagService.getAllTagResponsesByUserId(USER_ID)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/tags").param("userId", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(TAG_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("java"))
                .andExpect(jsonPath("$[0].color").value("#00FF00"));
    }

    @Test
    @DisplayName("GET /api/tags returns 400 when userId is missing")
    void getAllTagsRequiresUserId() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/tags/{id} returns 200 with the tag")
    void getTagByIdReturnsOk() throws Exception {
        when(tagService.getTagResponseById(TAG_ID)).thenReturn(response());

        mockMvc.perform(get("/api/tags/{id}", TAG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TAG_ID.toString()));
    }

    @Test
    @DisplayName("GET /api/tags/{id} returns 400 with the handler envelope for an unknown tag")
    void getTagByIdTranslatesBadRequestException() throws Exception {
        when(tagService.getTagResponseById(TAG_ID))
                .thenThrow(new BadRequestException("Tag not found with id: " + TAG_ID));

        mockMvc.perform(get("/api/tags/{id}", TAG_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details").value("Tag not found with id: " + TAG_ID));
    }

    @Test
    @DisplayName("GET /api/tags/search returns 200 with the filtered tags")
    void searchTagsReturnsOk() throws Exception {
        when(tagService.searchTagResponsesByName(USER_ID, "ja")).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/tags/search")
                        .param("userId", USER_ID.toString())
                        .param("name", "ja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("java"));
    }

    @Test
    @DisplayName("GET /api/tags/search returns 400 when the name filter is missing")
    void searchTagsRequiresName() throws Exception {
        mockMvc.perform(get("/api/tags/search").param("userId", USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/tags returns 201 with the created tag")
    void createTagReturnsCreated() throws Exception {
        TagRequestBody request = new TagRequestBody("java", "#00FF00", USER_ID);
        when(tagService.createTagFromRequest(any(TagRequestBody.class))).thenReturn(response());

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TAG_ID.toString()));
    }

    @Test
    @DisplayName("POST /api/tags returns 400 listing the field when the name is blank")
    void createTagRejectsBlankName() throws Exception {
        TagRequestBody request = new TagRequestBody("  ", "#00FF00", USER_ID);

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").value("name"))
                .andExpect(jsonPath("$.fieldsMessage").value("Name is required"));
    }

    @Test
    @DisplayName("POST /api/tags returns 400 listing the field when userId is absent")
    void createTagRejectsMissingUserId() throws Exception {
        TagRequestBody request = new TagRequestBody("java", "#00FF00", null);

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").value("userId"));
    }

    @Test
    @DisplayName("POST /api/tags returns 400 when the name is already taken")
    void createTagTranslatesDuplicateName() throws Exception {
        TagRequestBody request = new TagRequestBody("java", "#00FF00", USER_ID);
        when(tagService.createTagFromRequest(any(TagRequestBody.class)))
                .thenThrow(new BadRequestException("Tag with name 'java' already exists"));

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value("Tag with name 'java' already exists"));
    }

    @Test
    @DisplayName("PUT /api/tags/{id} returns 200 with the updated tag")
    void updateTagReturnsOk() throws Exception {
        TagRequestBody request = new TagRequestBody("java", "#123456", USER_ID);
        when(tagService.updateTagFromRequest(eq(TAG_ID), any(TagRequestBody.class))).thenReturn(response());

        mockMvc.perform(put("/api/tags/{id}", TAG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TAG_ID.toString()));
    }

    @Test
    @DisplayName("PUT /api/tags/{id} returns 400 when the name is blank")
    void updateTagRejectsBlankName() throws Exception {
        TagRequestBody request = new TagRequestBody("", "#123456", USER_ID);

        mockMvc.perform(put("/api/tags/{id}", TAG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").value("name"));
    }

    @Test
    @DisplayName("DELETE /api/tags/{id} returns 204 with no body")
    void deleteTagReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/tags/{id}", TAG_ID))
                .andExpect(status().isNoContent());

        verify(tagService).deleteTag(TAG_ID);
    }

    @Test
    @DisplayName("DELETE /api/tags/{id} returns 400 for an unknown tag")
    void deleteTagTranslatesBadRequestException() throws Exception {
        doThrow(new BadRequestException("Tag not found with id: " + TAG_ID))
                .when(tagService).deleteTag(TAG_ID);

        mockMvc.perform(delete("/api/tags/{id}", TAG_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value("Tag not found with id: " + TAG_ID));
    }
}
