package com.scriptum.backend.infrastructure.database;

import com.scriptum.backend.configuration.exception.BadRequestException;
import com.scriptum.backend.domain.entities.Note;
import com.scriptum.backend.infrastructure.database.jpa.Notes;
import com.scriptum.backend.infrastructure.database.jpa.UserJpaEntity;
import com.scriptum.backend.infrastructure.database.repository.INotesJpaRepository;
import com.scriptum.backend.infrastructure.database.repository.ITagJpaRepository;
import com.scriptum.backend.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
@DisplayName("NoteRepositoryImpl")
class NoteRepositoryImplTest {

    @Mock
    private INotesJpaRepository notesJpaRepository;

    @Mock
    private ITagJpaRepository tagJpaRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private NoteRepositoryImpl noteRepository;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TAG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final LocalDateTime MODIFIED_AT = LocalDateTime.of(2026, 1, 2, 10, 0);

    private static UserJpaEntity userEntity() {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(USER_ID);
        entity.setName("Original Name");
        entity.setEmail("user@example.invalid");
        entity.setPassword("hashed-password");
        return entity;
    }

    private static com.scriptum.backend.infrastructure.database.jpa.Tag tagEntity() {
        com.scriptum.backend.infrastructure.database.jpa.Tag entity =
                com.scriptum.backend.infrastructure.database.jpa.Tag.builder()
                        .id(TAG_ID)
                        .name("java")
                        .color("#00FF00")
                        .user(userEntity())
                        .build();
        // createdAt/modifiedAt live on BaseEntity and are set by @PrePersist, so the
        // builder cannot populate them; tests stamp them directly.
        ReflectionTestUtils.setField(entity, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(entity, "modifiedAt", MODIFIED_AT);
        return entity;
    }

    private static Notes notesEntity() {
        Notes entity = Notes.builder()
                .id(NOTE_ID)
                .title("first")
                .content("body text")
                .pinned(true)
                .user(userEntity())
                .tags(Set.of(tagEntity()))
                .build();
        ReflectionTestUtils.setField(entity, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(entity, "modifiedAt", MODIFIED_AT);
        return entity;
    }

    private static Note domainNote() {
        return Note.builder()
                .id(NOTE_ID)
                .title("first")
                .content("body text")
                .pinned(true)
                .userId(USER_ID)
                .tags(Set.of(com.scriptum.backend.domain.entities.Tag.builder()
                        .id(TAG_ID)
                        .name("java")
                        .color("#00FF00")
                        .userId(USER_ID)
                        .build()))
                .build();
    }

    @Test
    @DisplayName("findAllByUserId maps every persisted field onto the domain note")
    void findAllByUserIdMapsEveryField() {
        when(notesJpaRepository.findByUserIdOrderByModifiedAtDesc(USER_ID))
                .thenReturn(List.of(notesEntity()));

        List<Note> result = noteRepository.findAllByUserId(USER_ID);

        assertThat(result).singleElement().satisfies(note -> {
            assertThat(note.getId()).isEqualTo(NOTE_ID);
            assertThat(note.getTitle()).isEqualTo("first");
            assertThat(note.getContent()).isEqualTo("body text");
            assertThat(note.isPinned()).isTrue();
            assertThat(note.getUserId()).isEqualTo(USER_ID);
            assertThat(note.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(note.getModifiedAt()).isEqualTo(MODIFIED_AT);
            assertThat(note.getTags()).singleElement().satisfies(tag -> {
                assertThat(tag.getId()).isEqualTo(TAG_ID);
                assertThat(tag.getName()).isEqualTo("java");
                assertThat(tag.getColor()).isEqualTo("#00FF00");
                assertThat(tag.getUserId()).isEqualTo(USER_ID);
            });
        });
    }

    @Test
    @DisplayName("findAllByUserId leaves userId null when the persisted note has no owner")
    void findAllByUserIdToleratesMissingOwner() {
        Notes orphan = notesEntity();
        orphan.setUser(null);
        when(notesJpaRepository.findByUserIdOrderByModifiedAtDesc(USER_ID)).thenReturn(List.of(orphan));

        assertThat(noteRepository.findAllByUserId(USER_ID))
                .singleElement()
                .satisfies(note -> assertThat(note.getUserId()).isNull());
    }

    @Test
    @DisplayName("findById maps the persisted note when it exists")
    void findByIdMapsExistingNote() {
        when(notesJpaRepository.findById(NOTE_ID)).thenReturn(Optional.of(notesEntity()));

        assertThat(noteRepository.findById(NOTE_ID))
                .isPresent()
                .get()
                .satisfies(note -> assertThat(note.getTitle()).isEqualTo("first"));
    }

    @Test
    @DisplayName("findById returns an empty Optional when the note is absent")
    void findByIdReturnsEmptyWhenAbsent() {
        when(notesJpaRepository.findById(NOTE_ID)).thenReturn(Optional.empty());

        assertThat(noteRepository.findById(NOTE_ID)).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdAndTitleContaining delegates to the case-insensitive derived query")
    void findByTitleDelegatesToCaseInsensitiveQuery() {
        when(notesJpaRepository.findByUserIdAndTitleContainingIgnoreCaseOrderByModifiedAtDesc(USER_ID, "fir"))
                .thenReturn(List.of(notesEntity()));

        assertThat(noteRepository.findByUserIdAndTitleContaining(USER_ID, "fir")).hasSize(1);
        verify(notesJpaRepository)
                .findByUserIdAndTitleContainingIgnoreCaseOrderByModifiedAtDesc(USER_ID, "fir");
    }

    @Test
    @DisplayName("findByUserIdAndContentContaining delegates to the case-insensitive derived query")
    void findByContentDelegatesToCaseInsensitiveQuery() {
        when(notesJpaRepository.findByUserIdAndContentContainingIgnoreCaseOrderByModifiedAtDesc(USER_ID, "body"))
                .thenReturn(List.of(notesEntity()));

        assertThat(noteRepository.findByUserIdAndContentContaining(USER_ID, "body")).hasSize(1);
        verify(notesJpaRepository)
                .findByUserIdAndContentContainingIgnoreCaseOrderByModifiedAtDesc(USER_ID, "body");
    }

    @Test
    @DisplayName("findByUserIdAndTagId delegates to the join-table derived query")
    void findByTagDelegatesToJoinQuery() {
        when(notesJpaRepository.findByUserIdAndTagsIdOrderByModifiedAtDesc(USER_ID, TAG_ID))
                .thenReturn(List.of(notesEntity()));

        assertThat(noteRepository.findByUserIdAndTagId(USER_ID, TAG_ID)).hasSize(1);
        verify(notesJpaRepository).findByUserIdAndTagsIdOrderByModifiedAtDesc(USER_ID, TAG_ID);
    }

    @Test
    @DisplayName("save carries the scalar fields onto the persisted entity")
    void saveCarriesScalarFields() {
        when(userService.findByIdOrElseThrow(USER_ID)).thenReturn(userEntity());
        when(tagJpaRepository.findById(TAG_ID)).thenReturn(Optional.of(tagEntity()));
        when(notesJpaRepository.save(any(Notes.class))).thenAnswer(call -> call.getArgument(0));

        noteRepository.save(domainNote());

        ArgumentCaptor<Notes> saved = ArgumentCaptor.forClass(Notes.class);
        verify(notesJpaRepository).save(saved.capture());
        Notes entity = saved.getValue();

        assertThat(entity.getId()).isEqualTo(NOTE_ID);
        assertThat(entity.getTitle()).isEqualTo("first");
        assertThat(entity.getContent()).isEqualTo("body text");
        assertThat(entity.isPinned()).isTrue();
        assertThat(entity.getUser().getId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("save keeps the note's tags instead of silently dropping them")
    void saveKeepsTags() {
        when(userService.findByIdOrElseThrow(USER_ID)).thenReturn(userEntity());
        when(tagJpaRepository.findById(TAG_ID)).thenReturn(Optional.of(tagEntity()));
        when(notesJpaRepository.save(any(Notes.class))).thenAnswer(call -> call.getArgument(0));

        noteRepository.save(domainNote());

        ArgumentCaptor<Notes> saved = ArgumentCaptor.forClass(Notes.class);
        verify(notesJpaRepository).save(saved.capture());
        assertThat(saved.getValue().getTags())
                .extracting(com.scriptum.backend.infrastructure.database.jpa.Tag::getId)
                .containsExactly(TAG_ID);
    }

    @Test
    @DisplayName("save resolves tags to managed entities so the join table can be written")
    void saveResolvesTagsToManagedEntities() {
        com.scriptum.backend.infrastructure.database.jpa.Tag managed = tagEntity();
        when(userService.findByIdOrElseThrow(USER_ID)).thenReturn(userEntity());
        when(tagJpaRepository.findById(TAG_ID)).thenReturn(Optional.of(managed));
        when(notesJpaRepository.save(any(Notes.class))).thenAnswer(call -> call.getArgument(0));

        noteRepository.save(domainNote());

        ArgumentCaptor<Notes> saved = ArgumentCaptor.forClass(Notes.class);
        verify(notesJpaRepository).save(saved.capture());
        assertThat(saved.getValue().getTags()).containsExactly(managed);
    }

    @Test
    @DisplayName("save accepts a note with no tags")
    void saveAcceptsNoteWithoutTags() {
        Note untagged = domainNote();
        untagged.setTags(Set.of());
        when(userService.findByIdOrElseThrow(USER_ID)).thenReturn(userEntity());
        when(notesJpaRepository.save(any(Notes.class))).thenAnswer(call -> call.getArgument(0));

        noteRepository.save(untagged);

        ArgumentCaptor<Notes> saved = ArgumentCaptor.forClass(Notes.class);
        verify(notesJpaRepository).save(saved.capture());
        assertThat(saved.getValue().getTags()).isEmpty();
        verify(tagJpaRepository, never()).findById(any());
    }

    @Test
    @DisplayName("save rejects a tag id that no longer exists")
    void saveRejectsUnknownTag() {
        when(userService.findByIdOrElseThrow(USER_ID)).thenReturn(userEntity());
        when(tagJpaRepository.findById(TAG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteRepository.save(domainNote()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(TAG_ID.toString());

        verify(notesJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("save maps the persisted entity back to a domain note")
    void saveMapsPersistedEntityBack() {
        when(userService.findByIdOrElseThrow(USER_ID)).thenReturn(userEntity());
        when(tagJpaRepository.findById(TAG_ID)).thenReturn(Optional.of(tagEntity()));
        when(notesJpaRepository.save(any(Notes.class))).thenReturn(notesEntity());

        Note result = noteRepository.save(domainNote());

        assertThat(result.getId()).isEqualTo(NOTE_ID);
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(result.getTags()).hasSize(1);
    }

    @Test
    @DisplayName("save propagates the failure when the owning user does not exist")
    void savePropagatesUnknownOwner() {
        when(userService.findByIdOrElseThrow(USER_ID))
                .thenThrow(new IllegalArgumentException("User not found with id: " + USER_ID));

        assertThatThrownBy(() -> noteRepository.save(domainNote()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(notesJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteById delegates to the JPA repository")
    void deleteByIdDelegates() {
        noteRepository.deleteById(NOTE_ID);

        verify(notesJpaRepository).deleteById(NOTE_ID);
    }
}
