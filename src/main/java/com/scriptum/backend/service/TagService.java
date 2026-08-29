package com.scriptum.backend.service;

import com.scriptum.backend.configuration.exception.BadRequestException;
import com.scriptum.backend.domain.entities.Tag;
import com.scriptum.backend.domain.repositories.ITagRepository;
import com.scriptum.backend.domain.request.TagRequestBody;
import com.scriptum.backend.domain.response.TagResponseBody;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final ITagRepository tagRepository;
    private final UserService userService;

    public List<Tag> getAllTagsByUserId(UUID userId) {
        return tagRepository.findAllByUserId(userId);
    }

    public List<TagResponseBody> getAllTagResponsesByUserId(UUID userId) {
        List<Tag> tags = getAllTagsByUserId(userId);
        return mapToResponseBodyList(tags);
    }

    public Tag getTagById(UUID id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Tag not found with id: " + id));

        // Same "not found" response for a missing tag and someone else's tag, so IDs can't be enumerated
        if (!tag.getUserId().equals(userService.getCurrentUserId())) {
            throw new BadRequestException("Tag not found with id: " + id);
        }

        return tag;
    }

    public TagResponseBody getTagResponseById(UUID id) {
        Tag tag = getTagById(id);
        return mapToResponseBody(tag);
    }

    public Tag getTagByName(UUID userId, String name) {
        return tagRepository.findByUserIdAndName(userId, name)
                .orElseThrow(() -> new BadRequestException("Tag not found with name: " + name));
    }

    public TagResponseBody getTagResponseByName(UUID userId, String name) {
        Tag tag = getTagByName(userId, name);
        return mapToResponseBody(tag);
    }

    public List<Tag> searchTagsByName(UUID userId, String name) {
        return tagRepository.findByUserIdAndNameContaining(userId, name);
    }

    public List<TagResponseBody> searchTagResponsesByName(UUID userId, String name) {
        List<Tag> tags = searchTagsByName(userId, name);
        return mapToResponseBodyList(tags);
    }

    @Transactional
    public Tag createTag(Tag tag) {
        // Check if tag with same name already exists for this user
        tagRepository.findByUserIdAndName(tag.getUserId(), tag.getName())
                .ifPresent(existingTag -> {
                    throw new BadRequestException("Tag with name '" + tag.getName() + "' already exists");
                });

        return tagRepository.save(tag);
    }

    @Transactional
    public TagResponseBody createTagFromRequest(TagRequestBody requestBody) {
        Tag tag = mapToEntity(requestBody);
        Tag createdTag = createTag(tag);
        return mapToResponseBody(createdTag);
    }

    @Transactional
    public Tag updateTag(UUID id, Tag tagDetails) {
        Tag existingTag = getTagById(id);

        // Check if new name conflicts with existing tag
        if (!existingTag.getName().equals(tagDetails.getName())) {
            tagRepository.findByUserIdAndName(existingTag.getUserId(), tagDetails.getName())
                    .ifPresent(conflictTag -> {
                        throw new BadRequestException("Tag with name '" + tagDetails.getName() + "' already exists");
                    });
        }

        existingTag.setName(tagDetails.getName());
        existingTag.setColor(tagDetails.getColor());

        return tagRepository.save(existingTag);
    }

    @Transactional
    public TagResponseBody updateTagFromRequest(UUID id, TagRequestBody requestBody) {
        Tag tag = mapToEntity(requestBody);
        Tag updatedTag = updateTag(id, tag);
        return mapToResponseBody(updatedTag);
    }

    @Transactional
    public void deleteTag(UUID id) {
        getTagById(id); // Check if tag exists
        tagRepository.deleteById(id);
    }

    public Tag mapToEntity(TagRequestBody requestBody) {
        // userId is derived from the authenticated principal, never trusted from the request body
        return Tag.builder()
                .name(requestBody.getName())
                .color(requestBody.getColor())
                .userId(userService.getCurrentUserId())
                .build();
    }

    public TagResponseBody mapToResponseBody(Tag tag) {
        TagResponseBody responseBody = new TagResponseBody();
        responseBody.setId(tag.getId());
        responseBody.setName(tag.getName());
        responseBody.setColor(tag.getColor());
        responseBody.setUserId(tag.getUserId());
        responseBody.setCreatedAt(tag.getCreatedAt());
        responseBody.setModifiedAt(tag.getModifiedAt());
        return responseBody;
    }

    public List<TagResponseBody> mapToResponseBodyList(List<Tag> tags) {
        return tags.stream()
                .map(this::mapToResponseBody)
                .collect(Collectors.toList());
    }
}
