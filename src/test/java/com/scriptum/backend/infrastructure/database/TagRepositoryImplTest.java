package com.scriptum.backend.infrastructure.database;

import com.scriptum.backend.domain.entities.Tag;
import com.scriptum.backend.infrastructure.database.jpa.UserJpaEntity;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TagRepositoryImpl")
class TagRepositoryImplTest {

    @Mock
    private ITagJpaRepository tagJpaRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private TagRepositoryImpl tagRepository;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
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
        // createdAt/modifiedAt live on BaseEntity and are stamped by @PrePersist.
        ReflectionTestUtils.setField(entity, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(entity, "modifiedAt", MODIFIED_AT);
        return entity;
    }

    private static Tag domainTag() {
        return Tag.builder()
                .id(TAG_ID)
                .name("java")
                .color("#00FF00")
                .userId(USER_ID)
                .build();
    }

    @Test
    @DisplayName("findAllByUserId delegates to the name-ordered derived query and maps the results")
    void findAllByUserIdDelegatesAndMaps() {
        when(tagJpaRepository.findByUserIdOrderByNameAsc(USER_ID)).thenReturn(List.of(tagEntity()));

        List<Tag> result = tagRepository.findAllByUserId(USER_ID);

        assertThat(result).singleElement().satisfies(tag -> {
            assertThat(tag.getId()).isEqualTo(TAG_ID);
            assertThat(tag.getName()).isEqualTo("java");
            assertThat(tag.getColor()).isEqualTo("#00FF00");
            assertThat(tag.getUserId()).isEqualTo(USER_ID);
            assertThat(tag.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(tag.getModifiedAt()).isEqualTo(MODIFIED_AT);
        });
        verify(tagJpaRepository).findByUserIdOrderByNameAsc(USER_ID);
    }

    @Test
    @DisplayName("findAllByUserId leaves userId null when the persisted tag has no owner")
    void findAllByUserIdToleratesMissingOwner() {
        com.scriptum.backend.infrastructure.database.jpa.Tag orphan = tagEntity();
        orphan.setUser(null);
        when(tagJpaRepository.findByUserIdOrderByNameAsc(USER_ID)).thenReturn(List.of(orphan));

        assertThat(tagRepository.findAllByUserId(USER_ID))
                .singleElement()
                .satisfies(tag -> assertThat(tag.getUserId()).isNull());
    }

    @Test
    @DisplayName("findById maps the persisted tag when it exists")
    void findByIdMapsExistingTag() {
        when(tagJpaRepository.findById(TAG_ID)).thenReturn(Optional.of(tagEntity()));

        assertThat(tagRepository.findById(TAG_ID))
                .isPresent()
                .get()
                .satisfies(tag -> assertThat(tag.getName()).isEqualTo("java"));
    }

    @Test
    @DisplayName("findById returns an empty Optional when the tag is absent")
    void findByIdReturnsEmptyWhenAbsent() {
        when(tagJpaRepository.findById(TAG_ID)).thenReturn(Optional.empty());

        assertThat(tagRepository.findById(TAG_ID)).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdAndName scopes the lookup to the owning user")
    void findByUserIdAndNameScopesToUser() {
        when(tagJpaRepository.findByUserIdAndName(USER_ID, "java")).thenReturn(Optional.of(tagEntity()));

        assertThat(tagRepository.findByUserIdAndName(USER_ID, "java")).isPresent();
        verify(tagJpaRepository).findByUserIdAndName(USER_ID, "java");
    }

    @Test
    @DisplayName("findByUserIdAndNameContaining delegates to the case-insensitive derived query")
    void findByNameContainingDelegatesToCaseInsensitiveQuery() {
        when(tagJpaRepository.findByUserIdAndNameContainingIgnoreCaseOrderByNameAsc(USER_ID, "ja"))
                .thenReturn(List.of(tagEntity()));

        assertThat(tagRepository.findByUserIdAndNameContaining(USER_ID, "ja")).hasSize(1);
        verify(tagJpaRepository).findByUserIdAndNameContainingIgnoreCaseOrderByNameAsc(USER_ID, "ja");
    }

    @Test
    @DisplayName("save carries the scalar fields onto the persisted entity")
    void saveCarriesScalarFields() {
        when(userService.findByIdOrElseThrow(USER_ID)).thenReturn(userEntity());
        when(tagJpaRepository.save(any(com.scriptum.backend.infrastructure.database.jpa.Tag.class)))
                .thenAnswer(call -> call.getArgument(0));

        tagRepository.save(domainTag());

        ArgumentCaptor<com.scriptum.backend.infrastructure.database.jpa.Tag> saved =
                ArgumentCaptor.forClass(com.scriptum.backend.infrastructure.database.jpa.Tag.class);
        verify(tagJpaRepository).save(saved.capture());

        assertThat(saved.getValue().getId()).isEqualTo(TAG_ID);
        assertThat(saved.getValue().getName()).isEqualTo("java");
        assertThat(saved.getValue().getColor()).isEqualTo("#00FF00");
    }

    @Test
    @DisplayName("save binds the tag to its owning user instead of persisting it orphaned")
    void saveBindsOwningUser() {
        when(userService.findByIdOrElseThrow(USER_ID)).thenReturn(userEntity());
        when(tagJpaRepository.save(any(com.scriptum.backend.infrastructure.database.jpa.Tag.class)))
                .thenAnswer(call -> call.getArgument(0));

        tagRepository.save(domainTag());

        ArgumentCaptor<com.scriptum.backend.infrastructure.database.jpa.Tag> saved =
                ArgumentCaptor.forClass(com.scriptum.backend.infrastructure.database.jpa.Tag.class);
        verify(tagJpaRepository).save(saved.capture());

        // A tag saved with a null user gets USER_ID = null, which makes every
        // findByUserId* query blind to it and lets duplicate names slip through.
        assertThat(saved.getValue().getUser()).isNotNull();
        assertThat(saved.getValue().getUser().getId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("save maps the persisted entity back to a domain tag")
    void saveMapsPersistedEntityBack() {
        when(userService.findByIdOrElseThrow(USER_ID)).thenReturn(userEntity());
        when(tagJpaRepository.save(any(com.scriptum.backend.infrastructure.database.jpa.Tag.class)))
                .thenReturn(tagEntity());

        Tag result = tagRepository.save(domainTag());

        assertThat(result.getId()).isEqualTo(TAG_ID);
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(result.getModifiedAt()).isEqualTo(MODIFIED_AT);
    }

    @Test
    @DisplayName("save propagates the failure when the owning user does not exist")
    void savePropagatesUnknownOwner() {
        when(userService.findByIdOrElseThrow(USER_ID))
                .thenThrow(new IllegalArgumentException("User not found with id: " + USER_ID));

        assertThatThrownBy(() -> tagRepository.save(domainTag()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(USER_ID.toString());

        verify(tagJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteById delegates to the JPA repository")
    void deleteByIdDelegates() {
        tagRepository.deleteById(TAG_ID);

        verify(tagJpaRepository).deleteById(TAG_ID);
    }
}
