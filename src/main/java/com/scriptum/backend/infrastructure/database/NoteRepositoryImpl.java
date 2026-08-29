package com.scriptum.backend.infrastructure.database;

import com.scriptum.backend.domain.entities.Note;
import com.scriptum.backend.domain.entities.Tag;
import com.scriptum.backend.domain.repositories.INoteRepository;
import com.scriptum.backend.infrastructure.database.repository.INotesJpaRepository;
import com.scriptum.backend.infrastructure.database.repository.IUserJpaRepository;
import com.scriptum.backend.infrastructure.database.jpa.Notes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class NoteRepositoryImpl implements INoteRepository {

    private final INotesJpaRepository notesJpaRepository;
    private final IUserJpaRepository userJpaRepository;

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
                .pinned(note.isPinned())
                .content(note.getContent())
                .user(userJpaRepository.getReferenceById(note.getUserId()))
                .build();
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