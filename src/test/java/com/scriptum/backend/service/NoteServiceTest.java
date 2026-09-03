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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private NoteService noteService;

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
    @DisplayName("createNote resolves every tag id and attaches the tags before saving")
    void createNoteAttachesResolvedTags() {
        Tag resolved = tag(TAG_ID, "java");
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(resolved));
        when(noteRepository.save(any(Note.class))).thenAnswer(call -> call.getArgument(0));

        noteService.createNote(note(null, "first"), Set.of(TAG_ID));

        ArgumentCaptor<Note> saved = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(saved.capture());
        assertThat(saved.getValue().getTags()).containsExactly(resolved);
    }

    @Test
    @DisplayName("createNote throws BadRequestException and saves nothing when a tag id is unknown")
    void createNoteThrowsOnUnknownTag() {
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.createNote(note(null, "first"), Set.of(TAG_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(TAG_ID.toString());

        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("createNote accepts an empty tag id set and saves a note with no tags")
    void createNoteAcceptsEmptyTagSet() {
        when(noteRepository.save(any(Note.class))).thenAnswer(call -> call.getArgument(0));

        noteService.createNote(note(null, "first"), Set.of());

        ArgumentCaptor<Note> saved = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(saved.capture());
        assertThat(saved.getValue().getTags()).isEmpty();
    }

    @Test
    @DisplayName("createNote treats a null tag id set as no tags instead of failing")
    void createNoteTreatsNullTagSetAsEmpty() {
        when(noteRepository.save(any(Note.class))).thenAnswer(call -> call.getArgument(0));

        noteService.createNote(note(null, "first"), null);

        ArgumentCaptor<Note> saved = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(saved.capture());
        assertThat(saved.getValue().getTags()).isEmpty();
    }

    @Test
    @DisplayName("createNoteFromRequest maps the request, resolves tags and returns a response body")
    void createNoteFromRequestReturnsResponseBody() {
        NoteRequestBody request = new NoteRequestBody("first", "body text", USER_ID, Set.of(TAG_ID));
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(tag(TAG_ID, "java")));
        when(noteRepository.save(any(Note.class))).thenAnswer(call -> {
            Note argument = call.getArgument(0);
            argument.setId(NOTE_ID);
            return argument;
        });

        NoteResponseBody result = noteService.createNoteFromRequest(request);

        assertThat(result.getId()).isEqualTo(NOTE_ID);
        assertThat(result.getTitle()).isEqualTo("first");
        assertThat(result.getContent()).isEqualTo("body text");
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getTags()).extracting("name").containsExactly("java");
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
    @DisplayName("updateNote leaves the existing tags untouched when tagIds is null")
    void updateNoteKeepsTagsWhenTagIdsIsNull() {
        Tag existing = tag(TAG_ID, "java");
        Note stored = note(NOTE_ID, "old title");
        stored.setTags(Set.of(existing));
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(stored));
        when(noteRepository.save(any(Note.class))).thenAnswer(call -> call.getArgument(0));

        Note result = noteService.updateNote(NOTE_ID, Note.builder().title("t").content("c").build(), null);

        assertThat(result.getTags()).containsExactly(existing);
        verify(tagRepository, never()).findById(any());
    }

    @Test
    @DisplayName("updateNote replaces the tag set when tagIds is provided")
    void updateNoteReplacesTagsWhenTagIdsProvided() {
        Note stored = note(NOTE_ID, "old title");
        stored.setTags(Set.of(tag(UUID.randomUUID(), "old-tag")));
        Tag replacement = tag(TAG_ID, "new-tag");
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(stored));
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(replacement));
        when(noteRepository.save(any(Note.class))).thenAnswer(call -> call.getArgument(0));

        Note result = noteService.updateNote(
                NOTE_ID, Note.builder().title("t").content("c").build(), Set.of(TAG_ID));

        assertThat(result.getTags()).containsExactly(replacement);
    }

    @Test
    @DisplayName("updateNote throws BadRequestException and saves nothing when a tag id is unknown")
    void updateNoteThrowsOnUnknownTag() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(note(NOTE_ID, "old title")));
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.updateNote(
                NOTE_ID, Note.builder().title("t").content("c").build(), Set.of(TAG_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(TAG_ID.toString());

        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateNoteFromRequest returns the updated note as a response body")
    void updateNoteFromRequestReturnsResponseBody() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(note(NOTE_ID, "old title")));
        when(noteRepository.save(any(Note.class))).thenAnswer(call -> call.getArgument(0));

        NoteRequestBody request = new NoteRequestBody("new title", "new content", USER_ID, null);
        NoteResponseBody result = noteService.updateNoteFromRequest(NOTE_ID, request);

        assertThat(result.getId()).isEqualTo(NOTE_ID);
        assertThat(result.getTitle()).isEqualTo("new title");
        assertThat(result.getContent()).isEqualTo("new content");
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
    @DisplayName("mapToEntity copies title, content and userId and ignores tag ids")
    void mapToEntityCopiesScalarFields() {
        NoteRequestBody request = new NoteRequestBody("first", "body text", USER_ID, Set.of(TAG_ID));

        Note result = noteService.mapToEntity(request);

        assertThat(result.getTitle()).isEqualTo("first");
        assertThat(result.getContent()).isEqualTo("body text");
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getId()).isNull();
        assertThat(result.getTags()).isEmpty();
    }

    @Test
    @DisplayName("mapToResponseBodyList returns an empty list for no notes")
    void mapToResponseBodyListHandlesEmptyInput() {
        assertThat(noteService.mapToResponseBodyList(List.of())).isEmpty();
    }
}
