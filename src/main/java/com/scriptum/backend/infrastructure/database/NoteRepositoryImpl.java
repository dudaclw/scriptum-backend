package com.scriptum.backend.infrastructure.database;

import com.scriptum.backend.configuration.exception.BadRequestException;
import com.scriptum.backend.domain.entities.Note;
import com.scriptum.backend.domain.entities.Tag;
import com.scriptum.backend.domain.repositories.INoteRepository;
import com.scriptum.backend.infrastructure.database.jpa.Notes;
import com.scriptum.backend.infrastructure.database.repository.INotesJpaRepository;
import com.scriptum.backend.infrastructure.database.repository.ITagJpaRepository;
import com.scriptum.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class NoteRepositoryImpl implements INoteRepository {

    private final INotesJpaRepository notesJpaRepository;
    private final ITagJpaRepository tagJpaRepository;
    private final UserService userService;

    @Override
    public List<Note> findAllByUserId(UUID userId) {
        return notesJpaRepository.findByUserIdOrderByModifiedAtDesc(userId)
                .stream()
                .map(this::mapToNote)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Note> findById(UUID id) {
        return notesJpaRepository.findById(id)
                .map(this::mapToNote);
    }

    @Override
    public List<Note> findByUserIdAndTitleContaining(UUID userId, String title) {
        return notesJpaRepository.findByUserIdAndTitleContainingIgnoreCaseOrderByModifiedAtDesc(userId, title)
                .stream()
                .map(this::mapToNote)
                .collect(Collectors.toList());
    }

    @Override
    public List<Note> findByUserIdAndContentContaining(UUID userId, String content) {
        return notesJpaRepository.findByUserIdAndContentContainingIgnoreCaseOrderByModifiedAtDesc(userId, content)
                .stream()
                .map(this::mapToNote)
                .collect(Collectors.toList());
    }

    @Override
    public List<Note> findByUserIdAndTagId(UUID userId, UUID tagId) {
        return notesJpaRepository.findByUserIdAndTagsIdOrderByModifiedAtDesc(userId, tagId)
                .stream()
                .map(this::mapToNote)
                .collect(Collectors.toList());
    }

    @Override
    public Note save(Note note) {
        Notes notesJpa = mapToNotesJpa(note);
        Notes savedNotes = notesJpaRepository.save(notesJpa);
        return mapToNote(savedNotes);
    }

    @Override
    public void deleteById(UUID id) {
        notesJpaRepository.deleteById(id);
    }

    private Note mapToNote(Notes notes) {
        return Note.builder()
                .id(notes.getId())
                .title(notes.getTitle())
                .content(notes.getContent())
                .pinned(notes.isPinned())
                .userId(notes.getUser() != null ? notes.getUser().getId() : null)
                .createdAt(notes.getCreatedAt())
                .modifiedAt(notes.getModifiedAt())
                .tags(notes.getTags().stream()
                        .map(this::mapToTag)
                        .collect(Collectors.toSet()))
                .build();
    }

    private Notes mapToNotesJpa(Note note) {
        return Notes.builder()
                .id(note.getId())
                .title(note.getTitle())
                .content(note.getContent())
                .pinned(note.isPinned())
                .user(userService.findByIdOrElseThrow(note.getUserId()))
                .tags(resolveTagEntities(note.getTags()))
                .build();
    }

    /**
     * Reloads the note's tags as managed JPA entities.
     *
     * <p>The NOTE_TAGS join table can only be written from entities the persistence
     * context knows about, so the domain tags are resolved by id rather than rebuilt.
     */
    private Set<com.scriptum.backend.infrastructure.database.jpa.Tag> resolveTagEntities(Set<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return new HashSet<>();
        }

        return tags.stream()
                .map(tag -> tagJpaRepository.findById(tag.getId())
                        .orElseThrow(() -> new BadRequestException("Tag not found with id: " + tag.getId())))
                .collect(Collectors.toSet());
    }

    private Tag mapToTag(com.scriptum.backend.infrastructure.database.jpa.Tag tagJpa) {
        return Tag.builder()
                .id(tagJpa.getId())
                .name(tagJpa.getName())
                .color(tagJpa.getColor())
                .userId(tagJpa.getUser() != null ? tagJpa.getUser().getId() : null)
                .createdAt(tagJpa.getCreatedAt())
                .modifiedAt(tagJpa.getModifiedAt())
                .build();
    }
}
