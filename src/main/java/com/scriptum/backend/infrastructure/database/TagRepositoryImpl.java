package com.scriptum.backend.infrastructure.database;

import com.scriptum.backend.domain.entities.Tag;
import com.scriptum.backend.domain.repositories.ITagRepository;
import com.scriptum.backend.infrastructure.database.repository.ITagJpaRepository;
import com.scriptum.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TagRepositoryImpl implements ITagRepository {

    private final ITagJpaRepository tagJpaRepository;
    private final UserService userService;

    @Override
    public List<Tag> findAllByUserId(UUID userId) {
        return tagJpaRepository.findByUserIdOrderByNameAsc(userId)
                .stream()
                .map(this::mapToTag)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Tag> findById(UUID id) {
        return tagJpaRepository.findById(id)
                .map(this::mapToTag);
    }

    @Override
    public Optional<Tag> findByUserIdAndName(UUID userId, String name) {
        return tagJpaRepository.findByUserIdAndName(userId, name)
                .map(this::mapToTag);
    }

    @Override
    public List<Tag> findByUserIdAndNameContaining(UUID userId, String name) {
        return tagJpaRepository.findByUserIdAndNameContainingIgnoreCaseOrderByNameAsc(userId, name)
                .stream()
                .map(this::mapToTag)
                .collect(Collectors.toList());
    }

    @Override
    public Tag save(Tag tag) {
        com.scriptum.backend.infrastructure.database.jpa.Tag tagJpa = mapToTagJpa(tag);
        com.scriptum.backend.infrastructure.database.jpa.Tag savedTag = tagJpaRepository.save(tagJpa);
        return mapToTag(savedTag);
    }

    @Override
    public void deleteById(UUID id) {
        tagJpaRepository.deleteById(id);
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

    /**
     * Maps a domain tag onto its JPA entity, binding the owning user.
     *
     * <p>The owner must be resolved here: a tag persisted with a null USER_ID is
     * invisible to every findByUserId* query, which in turn lets duplicate tag
     * names through the uniqueness check in {@code TagService}.
     */
    private com.scriptum.backend.infrastructure.database.jpa.Tag mapToTagJpa(Tag tag) {
        return com.scriptum.backend.infrastructure.database.jpa.Tag.builder()
                .id(tag.getId())
                .name(tag.getName())
                .color(tag.getColor())
                .user(userService.findByIdOrElseThrow(tag.getUserId()))
                .build();
    }
}