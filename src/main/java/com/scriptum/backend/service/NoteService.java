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

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final INoteRepository noteRepository;
    private final ITagRepository tagRepository;
    private final UserService userService;

    public List<Note> getAllNotesByUserId(UUID userId) {
        return noteRepository.findAllByUserId(userId);
    }

    public List<NoteResponseBody> getAllNoteResponsesByUserId(UUID userId) {
        List<Note> notes = getAllNotesByUserId(userId);
        return mapToResponseBodyList(notes);
    }

    public Note getNoteById(UUID id) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Note not found with id: " + id));

        // Same "not found" response for a missing note and someone else's note, so IDs can't be enumerated
        if (!note.getUserId().equals(userService.getCurrentUserId())) {
            throw new BadRequestException("Note not found with id: " + id);
        }

        return note;
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
    public Note createNote(Note note, Set<Tag> tags) {
        note.setTags(tags);
        return noteRepository.save(note);
    }

    @Transactional
    public NoteResponseBody createNoteFromRequest(NoteRequestBody requestBody) {
        Note note = mapToEntity(requestBody);
        Set<Tag> tags = resolveTags(requestBody.getTags());
        Note createdNote = createNote(note, tags);
        return mapToResponseBody(createdNote);
    }

    @Transactional
    public Note updateNote(UUID id, Note noteDetails, Set<Tag> tags) {
        Note existingNote = getNoteById(id);

        existingNote.setTitle(noteDetails.getTitle());
        existingNote.setContent(noteDetails.getContent());
        existingNote.setColor(noteDetails.getColor());
        existingNote.setPinned(noteDetails.isPinned());

        if (tags != null) {
            existingNote.setTags(tags);
        }

        return noteRepository.save(existingNote);
    }

    @Transactional
    public NoteResponseBody updateNoteFromRequest(UUID id, NoteRequestBody requestBody) {
        Note note = mapToEntity(requestBody);
        Set<Tag> tags = resolveTags(requestBody.getTags());
        Note updatedNote = updateNote(id, note, tags);
        return mapToResponseBody(updatedNote);
    }

    // Notes are tagged by name from the client (free-typed, not pre-created), so
    // attaching a tag means find-or-create by name for the current user.
    private Set<Tag> resolveTags(List<NoteRequestBody.TagRef> tagRefs) {
        if (tagRefs == null) {
            return Set.of();
        }

        UUID userId = userService.getCurrentUserId();
        return tagRefs.stream()
                .map(ref -> tagRepository.findByUserIdAndName(userId, ref.name())
                        .orElseGet(() -> tagRepository.save(Tag.builder()
                                .name(ref.name())
                                .color(ref.color())
                                .userId(userId)
                                .build())))
                .collect(Collectors.toSet());
    }

    @Transactional
    public void deleteNote(UUID id) {
        getNoteById(id); // Check if note exists
        noteRepository.deleteById(id);
    }

    public Note mapToEntity(NoteRequestBody requestBody) {
        // userId is derived from the authenticated principal, never trusted from the request body
        return Note.builder()
                .title(requestBody.getTitle())
                .content(requestBody.getContent())
                .color(requestBody.getColor())
                .pinned(requestBody.isPinned())
                .userId(userService.getCurrentUserId())
                .build();
    }

    public NoteResponseBody mapToResponseBody(Note note) {
        NoteResponseBody responseBody = new NoteResponseBody();
        responseBody.setId(note.getId());
        responseBody.setTitle(note.getTitle());
        responseBody.setContent(note.getContent());
        responseBody.setColor(note.getColor());
        responseBody.setPinned(note.isPinned());
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
