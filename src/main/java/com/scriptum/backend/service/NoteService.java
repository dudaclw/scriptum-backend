package com.scriptum.backend.service;

import com.scriptum.backend.configuration.exception.BadRequestException;
import com.scriptum.backend.domain.entities.Note;
import com.scriptum.backend.domain.entities.Tag;
import com.scriptum.backend.domain.repositories.INoteRepository;
import com.scriptum.backend.domain.repositories.ITagRepository;
import com.scriptum.backend.domain.request.NoteRequestBody;
import com.scriptum.backend.domain.response.NoteResponseBody;
import com.scriptum.backend.domain.response.TagResponseBody;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final INoteRepository noteRepository;
    private final ITagRepository tagRepository;

    public List<Note> getAllNotesByUserId(UUID userId) {
        return noteRepository.findAllByUserId(userId);
    }

    public List<NoteResponseBody> getAllNoteResponsesByUserId(UUID userId) {
        List<Note> notes = getAllNotesByUserId(userId);
        return mapToResponseBodyList(notes);
    }

    public Note getNoteById(UUID id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Note not found with id: " + id));
    }

    public NoteResponseBody getNoteResponseById(UUID id) {
        Note note = getNoteById(id);
        return mapToResponseBody(note);
    }

    public List<Note> searchNotesByTitle(UUID userId, String title) {
        return noteRepository.findByUserIdAndTitleContaining(userId, title);
    }

    public List<NoteResponseBody> searchNoteResponsesByTitle(UUID userId, String title) {
        List<Note> notes = searchNotesByTitle(userId, title);
        return mapToResponseBodyList(notes);
    }

    public List<Note> searchNotesByContent(UUID userId, String content) {
        return noteRepository.findByUserIdAndContentContaining(userId, content);
    }

    public List<NoteResponseBody> searchNoteResponsesByContent(UUID userId, String content) {
        List<Note> notes = searchNotesByContent(userId, content);
        return mapToResponseBodyList(notes);
    }

    public List<Note> getNotesByTag(UUID userId, UUID tagId) {
        return noteRepository.findByUserIdAndTagId(userId, tagId);
    }

    public List<NoteResponseBody> getNoteResponsesByTag(UUID userId, UUID tagId) {
        List<Note> notes = getNotesByTag(userId, tagId);
        return mapToResponseBodyList(notes);
    }

    @Transactional
    public Note createNote(Note note, Set<UUID> tagIds) {
        note.setTags(resolveTags(tagIds));
        return noteRepository.save(note);
    }

    @Transactional
    public NoteResponseBody createNoteFromRequest(NoteRequestBody requestBody) {
        Note note = mapToEntity(requestBody);
        Note createdNote = createNote(note, requestBody.getTagIds());
        return mapToResponseBody(createdNote);
    }

    @Transactional
    public Note updateNote(UUID id, Note noteDetails, Set<UUID> tagIds) {
        Note existingNote = getNoteById(id);

        existingNote.setTitle(noteDetails.getTitle());
        existingNote.setContent(noteDetails.getContent());

        // A null tagIds means "leave the current tags alone"; an empty set clears them.
        if (tagIds != null) {
            existingNote.setTags(resolveTags(tagIds));
        }

        return noteRepository.save(existingNote);
    }

    private Set<Tag> resolveTags(Set<UUID> tagIds) {
        if (tagIds == null) {
            return new HashSet<>();
        }

        return tagIds.stream()
                .map(tagId -> tagRepository.findById(tagId)
                        .orElseThrow(() -> new BadRequestException("Tag not found with id: " + tagId)))
                .collect(Collectors.toSet());
    }

    @Transactional
    public NoteResponseBody updateNoteFromRequest(UUID id, NoteRequestBody requestBody) {
        Note note = mapToEntity(requestBody);
        Note updatedNote = updateNote(id, note, requestBody.getTagIds());
        return mapToResponseBody(updatedNote);
    }

    @Transactional
    public void deleteNote(UUID id) {
        getNoteById(id);
        noteRepository.deleteById(id);
    }

    public Note mapToEntity(NoteRequestBody requestBody) {
        return Note.builder()
                .title(requestBody.getTitle())
                .content(requestBody.getContent())
                .userId(requestBody.getUserId())
                .build();
    }

    public NoteResponseBody mapToResponseBody(Note note) {
        NoteResponseBody responseBody = new NoteResponseBody();
        responseBody.setId(note.getId());
        responseBody.setTitle(note.getTitle());
        responseBody.setContent(note.getContent());
        responseBody.setUserId(note.getUserId());
        responseBody.setCreatedAt(note.getCreatedAt());
        responseBody.setModifiedAt(note.getModifiedAt());

        if (note.getTags() != null) {
            responseBody.setTags(note.getTags().stream()
                    .map(tag -> {
                        TagResponseBody tagResponse = new TagResponseBody();
                        tagResponse.setId(tag.getId());
                        tagResponse.setName(tag.getName());
                        tagResponse.setColor(tag.getColor());
                        tagResponse.setUserId(tag.getUserId());
                        tagResponse.setCreatedAt(tag.getCreatedAt());
                        tagResponse.setModifiedAt(tag.getModifiedAt());
                        return tagResponse;
                    })
                    .collect(Collectors.toSet()));
        }

        return responseBody;
    }

    public List<NoteResponseBody> mapToResponseBodyList(List<Note> notes) {
        return notes.stream()
                .map(this::mapToResponseBody)
                .collect(Collectors.toList());
    }
}
