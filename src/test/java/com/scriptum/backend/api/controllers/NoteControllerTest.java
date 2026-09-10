package com.scriptum.backend.api.controllers;

import com.scriptum.backend.configuration.exception.BadRequestException;
import com.scriptum.backend.domain.request.NoteRequestBody;
import com.scriptum.backend.domain.response.NoteResponseBody;
import com.scriptum.backend.service.NoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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

@WebLayerTest(NoteController.class)
@DisplayName("NoteController")
class NoteControllerTest extends WebLayerTestSupport {

    @MockitoBean
    private NoteService noteService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TAG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static NoteResponseBody response() {
        NoteResponseBody body = new NoteResponseBody();
        body.setId(NOTE_ID);
        body.setTitle("first");
        body.setContent("body text");
        body.setUserId(USER_ID);
        body.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        body.setModifiedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        return body;
    }

    @Test
    @DisplayName("GET /api/notes returns 200 with the caller's notes")
    void getAllNotesReturnsOk() throws Exception {
        when(noteService.getAllNoteResponsesByUserId(USER_ID)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/notes").param("userId", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(NOTE_ID.toString()))
                .andExpect(jsonPath("$[0].title").value("first"))
                .andExpect(jsonPath("$[0].userId").value(USER_ID.toString()));
    }

    @Test
    @DisplayName("GET /api/notes returns 400 when userId is missing")
    void getAllNotesRequiresUserId() throws Exception {
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/notes returns 400 when userId is not a UUID")
    void getAllNotesRejectsMalformedUserId() throws Exception {
        mockMvc.perform(get("/api/notes").param("userId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/notes/{id} returns 200 with the note")
    void getNoteByIdReturnsOk() throws Exception {
        when(noteService.getNoteResponseById(NOTE_ID)).thenReturn(response());

        mockMvc.perform(get("/api/notes/{id}", NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(NOTE_ID.toString()))
                .andExpect(jsonPath("$.content").value("body text"));
    }

    @Test
    @DisplayName("GET /api/notes/{id} returns 400 with the handler envelope for an unknown note")
    void getNoteByIdTranslatesBadRequestException() throws Exception {
        when(noteService.getNoteResponseById(NOTE_ID))
                .thenThrow(new BadRequestException("Note not found with id: " + NOTE_ID));

        mockMvc.perform(get("/api/notes/{id}", NOTE_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details").value("Note not found with id: " + NOTE_ID))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/notes/search/title returns 200 with the filtered notes")
    void searchByTitleReturnsOk() throws Exception {
        when(noteService.searchNoteResponsesByTitle(USER_ID, "fir")).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/notes/search/title")
                        .param("userId", USER_ID.toString())
                        .param("title", "fir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("first"));
    }

    @Test
    @DisplayName("GET /api/notes/search/content returns 200 with the filtered notes")
    void searchByContentReturnsOk() throws Exception {
        when(noteService.searchNoteResponsesByContent(USER_ID, "body")).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/notes/search/content")
                        .param("userId", USER_ID.toString())
                        .param("content", "body"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/notes/tag/{tagId} returns 200 with the notes carrying that tag")
    void getNotesByTagReturnsOk() throws Exception {
        when(noteService.getNoteResponsesByTag(USER_ID, TAG_ID)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/notes/tag/{tagId}", TAG_ID).param("userId", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(NOTE_ID.toString()));
    }

    @Test
    @DisplayName("POST /api/notes returns 400 for a malformed JSON body")
    void createNoteRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/notes/{id} returns 204 with no body")
    void deleteNoteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/notes/{id}", NOTE_ID))
                .andExpect(status().isNoContent());

        verify(noteService).deleteNote(NOTE_ID);
    }

    @Test
    @DisplayName("DELETE /api/notes/{id} returns 400 for an unknown note")
    void deleteNoteTranslatesBadRequestException() throws Exception {
        doThrow(new BadRequestException("Note not found with id: " + NOTE_ID))
                .when(noteService).deleteNote(NOTE_ID);

        mockMvc.perform(delete("/api/notes/{id}", NOTE_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value("Note not found with id: " + NOTE_ID));
    }
}
