package com.scriptum.backend.service;

import com.scriptum.backend.configuration.exception.BadRequestException;
import com.scriptum.backend.domain.entities.Note;
import com.scriptum.backend.domain.entities.Tag;
import com.scriptum.backend.domain.repositories.INoteRepository;
import com.scriptum.backend.domain.repositories.ITagRepository;
import com.scriptum.backend.domain.request.NoteRequestBody;
import com.scriptum.backend.domain.response.NoteResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NoteService")
class NoteServiceTest {

    @Mock
    private INoteRepository noteRepository;

    @Mock
    private ITagRepository tagRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private NoteService noteService;

    @BeforeEach
    void stubCurrentUser() {
        // getNoteById and friends compare the note's owner against the caller.
        // Lenient because the read-only tests below never reach that branch.
        lenient().when(userService.getCurrentUserId()).thenReturn(USER_ID);
    }

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TAG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final LocalDateTime MODIFIED_AT = LocalDateTime.of(2026, 1, 2, 10, 0);

    private static Tag tag(UUID id, String name) {
        return Tag.builder()
                .id(id)
                .name(name)
                .color("#FF0000")
                .userId(USER_ID)
                .createdAt(CREATED_AT)
                .modifiedAt(MODIFIED_AT)
                .build();
    }

    private static Note note(UUID id, String title) {
        return Note.builder()
                .id(id)
                .title(title)
                .content("content of " + title)
                .userId(USER_ID)
                .createdAt(CREATED_AT)
                .modifiedAt(MODIFIED_AT)
                .build();
    }

    @Test
    @DisplayName("getAllNotesByUserId delegates to the repository")
    void getAllNotesByUserIdDelegatesToRepository() {
        List<Note> stored = List.of(note(NOTE_ID, "first"));
        when(noteRepository.findAllByUserId(USER_ID)).thenReturn(stored);

        List<Note> result = noteService.getAllNotesByUserId(USER_ID);

        assertThat(result).isEqualTo(stored);
        verify(noteRepository).findAllByUserId(USER_ID);
    }

    @Test
    @DisplayName("getAllNoteResponsesByUserId maps every note to a response body")
    void getAllNoteResponsesByUserIdMapsEveryNote() {
        when(noteRepository.findAllByUserId(USER_ID))
                .thenReturn(List.of(note(NOTE_ID, "first"), note(UUID.randomUUID(), "second")));

        List<NoteResponseBody> result = noteService.getAllNoteResponsesByUserId(USER_ID);

        assertThat(result).extracting(NoteResponseBody::getTitle).containsExactly("first", "second");
        assertThat(result).allSatisfy(body -> assertThat(body.getUserId()).isEqualTo(USER_ID));
    }

    @Test
    @DisplayName("getNoteById returns the note when it exists")
    void getNoteByIdReturnsExistingNote() {
        Note stored = note(NOTE_ID, "first");
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(stored));

        Note result = noteService.getNoteById(NOTE_ID);

        assertThat(result).isSameAs(stored);
    }

    @Test
    @DisplayName("getNoteById throws BadRequestException naming the id when the note is missing")
    void getNoteByIdThrowsWhenMissing() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getNoteById(NOTE_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(NOTE_ID.toString());
    }

    @Test
    @DisplayName("getNoteResponseById maps the stored note to a response body")
    void getNoteResponseByIdMapsStoredNote() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(note(NOTE_ID, "first")));

        NoteResponseBody result = noteService.getNoteResponseById(NOTE_ID);

        assertThat(result.getId()).isEqualTo(NOTE_ID);
        assertThat(result.getTitle()).isEqualTo("first");
    }

    @Test
    @DisplayName("updateNote overwrites title and content on the stored note")
    void updateNoteOverwritesTitleAndContent() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(note(NOTE_ID, "old title")));
        when(noteRepository.save(any(Note.class))).thenAnswer(call -> call.getArgument(0));

        Note details = Note.builder().title("new title").content("new content").build();
        Note result = noteService.updateNote(NOTE_ID, details, null);

        assertThat(result.getTitle()).isEqualTo("new title");
        assertThat(result.getContent()).isEqualTo("new content");
        assertThat(result.getId()).isEqualTo(NOTE_ID);
    }

    @Test
    @DisplayName("deleteNote checks the note exists before deleting it")
    void deleteNoteChecksExistenceFirst() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(note(NOTE_ID, "first")));

        noteService.deleteNote(NOTE_ID);

        verify(noteRepository).findById(NOTE_ID);
        verify(noteRepository).deleteById(NOTE_ID);
    }

    @Test
    @DisplayName("deleteNote throws BadRequestException and deletes nothing when the note is missing")
    void deleteNoteThrowsWhenMissing() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.deleteNote(NOTE_ID))
                .isInstanceOf(BadRequestException.class);

        verify(noteRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("searchNoteResponsesByTitle delegates the title filter and maps the results")
    void searchNoteResponsesByTitleDelegatesAndMaps() {
        when(noteRepository.findByUserIdAndTitleContaining(USER_ID, "fir"))
                .thenReturn(List.of(note(NOTE_ID, "first")));

        List<NoteResponseBody> result = noteService.searchNoteResponsesByTitle(USER_ID, "fir");

        assertThat(result).extracting(NoteResponseBody::getTitle).containsExactly("first");
        verify(noteRepository).findByUserIdAndTitleContaining(USER_ID, "fir");
    }

    @Test
    @DisplayName("searchNoteResponsesByContent delegates the content filter and maps the results")
    void searchNoteResponsesByContentDelegatesAndMaps() {
        when(noteRepository.findByUserIdAndContentContaining(USER_ID, "body"))
                .thenReturn(List.of(note(NOTE_ID, "first")));

        List<NoteResponseBody> result = noteService.searchNoteResponsesByContent(USER_ID, "body");

        assertThat(result).hasSize(1);
        verify(noteRepository).findByUserIdAndContentContaining(USER_ID, "body");
    }

    @Test
    @DisplayName("getNoteResponsesByTag delegates the tag filter and maps the results")
    void getNoteResponsesByTagDelegatesAndMaps() {
        when(noteRepository.findByUserIdAndTagId(USER_ID, TAG_ID))
                .thenReturn(List.of(note(NOTE_ID, "first")));

        List<NoteResponseBody> result = noteService.getNoteResponsesByTag(USER_ID, TAG_ID);

        assertThat(result).hasSize(1);
        verify(noteRepository).findByUserIdAndTagId(USER_ID, TAG_ID);
    }

    @Test
    @DisplayName("mapToResponseBody copies timestamps and nested tag fields")
    void mapToResponseBodyCopiesTimestampsAndTags() {
        Note stored = note(NOTE_ID, "first");
        stored.setTags(Set.of(tag(TAG_ID, "java")));

        NoteResponseBody result = noteService.mapToResponseBody(stored);

        assertThat(result.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(result.getModifiedAt()).isEqualTo(MODIFIED_AT);
        assertThat(result.getTags()).singleElement().satisfies(mapped -> {
            assertThat(mapped.getId()).isEqualTo(TAG_ID);
            assertThat(mapped.getName()).isEqualTo("java");
            assertThat(mapped.getColor()).isEqualTo("#FF0000");
            assertThat(mapped.getUserId()).isEqualTo(USER_ID);
            assertThat(mapped.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(mapped.getModifiedAt()).isEqualTo(MODIFIED_AT);
        });
    }

    @Test
    @DisplayName("mapToResponseBody yields an empty tag set when the note has no tags")
    void mapToResponseBodyYieldsEmptyTagsWhenAbsent() {
        Note stored = note(NOTE_ID, "first");
        stored.setTags(null);

        NoteResponseBody result = noteService.mapToResponseBody(stored);

        // NoteResponseBody initialises tags to an empty HashSet, so a note without
        // tags serialises as [] rather than null. Clients never see a null array.
        assertThat(result.getTags()).isEmpty();
        assertThat(result.getTitle()).isEqualTo("first");
    }

    @Test
    @DisplayName("mapToResponseBodyList returns an empty list for no notes")
    void mapToResponseBodyListHandlesEmptyInput() {
        assertThat(noteService.mapToResponseBodyList(List.of())).isEmpty();
    }
}
