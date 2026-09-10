package com.scriptum.backend.service;

import com.scriptum.backend.configuration.exception.BadRequestException;
import com.scriptum.backend.domain.entities.Tag;
import com.scriptum.backend.domain.repositories.ITagRepository;
import com.scriptum.backend.domain.request.TagRequestBody;
import com.scriptum.backend.domain.response.TagResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TagService")
class TagServiceTest {

    @Mock
    private ITagRepository tagRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private TagService tagService;

    @BeforeEach
    void stubCurrentUser() {
        // getTagById and friends compare the tag's owner against the caller.
        lenient().when(userService.getCurrentUserId()).thenReturn(USER_ID);
    }

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TAG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final LocalDateTime MODIFIED_AT = LocalDateTime.of(2026, 1, 2, 10, 0);

    private static Tag tag(UUID id, String name) {
        return Tag.builder()
                .id(id)
                .name(name)
                .color("#00FF00")
                .userId(USER_ID)
                .createdAt(CREATED_AT)
                .modifiedAt(MODIFIED_AT)
                .build();
    }

    @Test
    @DisplayName("getAllTagsByUserId delegates to the repository")
    void getAllTagsByUserIdDelegatesToRepository() {
        List<Tag> stored = List.of(tag(TAG_ID, "java"));
        when(tagRepository.findAllByUserId(USER_ID)).thenReturn(stored);

        assertThat(tagService.getAllTagsByUserId(USER_ID)).isEqualTo(stored);
        verify(tagRepository).findAllByUserId(USER_ID);
    }

    @Test
    @DisplayName("getAllTagResponsesByUserId maps every tag to a response body")
    void getAllTagResponsesByUserIdMapsEveryTag() {
        when(tagRepository.findAllByUserId(USER_ID))
                .thenReturn(List.of(tag(TAG_ID, "java"), tag(UUID.randomUUID(), "spring")));

        List<TagResponseBody> result = tagService.getAllTagResponsesByUserId(USER_ID);

        assertThat(result).extracting(TagResponseBody::getName).containsExactly("java", "spring");
    }

    @Test
    @DisplayName("getTagById returns the tag when it exists")
    void getTagByIdReturnsExistingTag() {
        Tag stored = tag(TAG_ID, "java");
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(stored));

        assertThat(tagService.getTagById(TAG_ID)).isSameAs(stored);
    }

    @Test
    @DisplayName("getTagById throws BadRequestException naming the id when the tag is missing")
    void getTagByIdThrowsWhenMissing() {
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.getTagById(TAG_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(TAG_ID.toString());
    }

    @Test
    @DisplayName("getTagResponseById maps the stored tag to a response body")
    void getTagResponseByIdMapsStoredTag() {
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(tag(TAG_ID, "java")));

        TagResponseBody result = tagService.getTagResponseById(TAG_ID);

        assertThat(result.getId()).isEqualTo(TAG_ID);
        assertThat(result.getName()).isEqualTo("java");
    }

    @Test
    @DisplayName("getTagByName scopes the lookup to the owning user")
    void getTagByNameScopesLookupToUser() {
        Tag stored = tag(TAG_ID, "java");
        when(tagRepository.findByUserIdAndName(USER_ID, "java")).thenReturn(Optional.of(stored));

        assertThat(tagService.getTagByName(USER_ID, "java")).isSameAs(stored);
        verify(tagRepository).findByUserIdAndName(USER_ID, "java");
    }

    @Test
    @DisplayName("getTagByName throws BadRequestException naming the name when the tag is missing")
    void getTagByNameThrowsWhenMissing() {
        when(tagRepository.findByUserIdAndName(USER_ID, "ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.getTagByName(USER_ID, "ghost"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    @DisplayName("getTagResponseByName maps the stored tag to a response body")
    void getTagResponseByNameMapsStoredTag() {
        when(tagRepository.findByUserIdAndName(USER_ID, "java")).thenReturn(Optional.of(tag(TAG_ID, "java")));

        assertThat(tagService.getTagResponseByName(USER_ID, "java").getId()).isEqualTo(TAG_ID);
    }

    @Test
    @DisplayName("searchTagResponsesByName delegates the name filter and maps the results")
    void searchTagResponsesByNameDelegatesAndMaps() {
        when(tagRepository.findByUserIdAndNameContaining(USER_ID, "ja"))
                .thenReturn(List.of(tag(TAG_ID, "java")));

        List<TagResponseBody> result = tagService.searchTagResponsesByName(USER_ID, "ja");

        assertThat(result).extracting(TagResponseBody::getName).containsExactly("java");
        verify(tagRepository).findByUserIdAndNameContaining(USER_ID, "ja");
    }

    @Test
    @DisplayName("createTag saves the tag when the user has no tag with that name")
    void createTagSavesWhenNameIsFree() {
        Tag incoming = tag(null, "java");
        when(tagRepository.findByUserIdAndName(USER_ID, "java")).thenReturn(Optional.empty());
        when(tagRepository.save(incoming)).thenReturn(tag(TAG_ID, "java"));

        Tag result = tagService.createTag(incoming);

        assertThat(result.getId()).isEqualTo(TAG_ID);
        verify(tagRepository).save(incoming);
    }

    @Test
    @DisplayName("createTag throws BadRequestException and saves nothing on a duplicate name")
    void createTagThrowsOnDuplicateName() {
        when(tagRepository.findByUserIdAndName(USER_ID, "java")).thenReturn(Optional.of(tag(TAG_ID, "java")));

        assertThatThrownBy(() -> tagService.createTag(tag(null, "java")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("java");

        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTagFromRequest maps the request and returns a response body")
    void createTagFromRequestReturnsResponseBody() {
        TagRequestBody request = new TagRequestBody("java", "#00FF00", USER_ID);
        when(tagRepository.findByUserIdAndName(USER_ID, "java")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(call -> {
            Tag argument = call.getArgument(0);
            argument.setId(TAG_ID);
            return argument;
        });

        TagResponseBody result = tagService.createTagFromRequest(request);

        assertThat(result.getId()).isEqualTo(TAG_ID);
        assertThat(result.getName()).isEqualTo("java");
        assertThat(result.getColor()).isEqualTo("#00FF00");
        assertThat(result.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("updateTag overwrites name and colour on the stored tag")
    void updateTagOverwritesNameAndColour() {
        Tag stored = tag(TAG_ID, "old-name");
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(stored));
        when(tagRepository.findByUserIdAndName(USER_ID, "new-name")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(call -> call.getArgument(0));

        Tag details = Tag.builder().name("new-name").color("#123456").build();
        Tag result = tagService.updateTag(TAG_ID, details);

        assertThat(result.getName()).isEqualTo("new-name");
        assertThat(result.getColor()).isEqualTo("#123456");
        assertThat(result.getId()).isEqualTo(TAG_ID);
    }

    @Test
    @DisplayName("updateTag skips the conflict lookup when the name is unchanged")
    void updateTagSkipsConflictLookupWhenNameUnchanged() {
        Tag stored = tag(TAG_ID, "java");
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(stored));
        when(tagRepository.save(any(Tag.class))).thenAnswer(call -> call.getArgument(0));

        Tag result = tagService.updateTag(TAG_ID, Tag.builder().name("java").color("#123456").build());

        assertThat(result.getColor()).isEqualTo("#123456");
        verify(tagRepository, never()).findByUserIdAndName(any(), anyString());
    }

    @Test
    @DisplayName("updateTag throws BadRequestException and saves nothing when the new name is taken")
    void updateTagThrowsOnConflictingName() {
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(tag(TAG_ID, "old-name")));
        when(tagRepository.findByUserIdAndName(USER_ID, "taken"))
                .thenReturn(Optional.of(tag(UUID.randomUUID(), "taken")));

        assertThatThrownBy(() -> tagService.updateTag(TAG_ID, Tag.builder().name("taken").build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("taken");

        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTag throws BadRequestException when the tag does not exist")
    void updateTagThrowsWhenTagMissing() {
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.updateTag(TAG_ID, Tag.builder().name("java").build()))
                .isInstanceOf(BadRequestException.class);

        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTagFromRequest returns the updated tag as a response body")
    void updateTagFromRequestReturnsResponseBody() {
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(tag(TAG_ID, "old-name")));
        when(tagRepository.findByUserIdAndName(USER_ID, "new-name")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(call -> call.getArgument(0));

        TagResponseBody result = tagService.updateTagFromRequest(
                TAG_ID, new TagRequestBody("new-name", "#123456", USER_ID));

        assertThat(result.getId()).isEqualTo(TAG_ID);
        assertThat(result.getName()).isEqualTo("new-name");
        assertThat(result.getColor()).isEqualTo("#123456");
    }

    @Test
    @DisplayName("deleteTag checks the tag exists before deleting it")
    void deleteTagChecksExistenceFirst() {
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(tag(TAG_ID, "java")));

        tagService.deleteTag(TAG_ID);

        verify(tagRepository).findById(TAG_ID);
        verify(tagRepository).deleteById(TAG_ID);
    }

    @Test
    @DisplayName("deleteTag throws BadRequestException and deletes nothing when the tag is missing")
    void deleteTagThrowsWhenMissing() {
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.deleteTag(TAG_ID))
                .isInstanceOf(BadRequestException.class);

        verify(tagRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("mapToEntity copies name, colour and userId and leaves the id unset")
    void mapToEntityCopiesScalarFields() {
        Tag result = tagService.mapToEntity(new TagRequestBody("java", "#00FF00", USER_ID));

        assertThat(result.getName()).isEqualTo("java");
        assertThat(result.getColor()).isEqualTo("#00FF00");
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getId()).isNull();
    }

    @Test
    @DisplayName("mapToResponseBody copies every field including timestamps")
    void mapToResponseBodyCopiesEveryField() {
        TagResponseBody result = tagService.mapToResponseBody(tag(TAG_ID, "java"));

        assertThat(result.getId()).isEqualTo(TAG_ID);
        assertThat(result.getName()).isEqualTo("java");
        assertThat(result.getColor()).isEqualTo("#00FF00");
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(result.getModifiedAt()).isEqualTo(MODIFIED_AT);
    }

    @Test
    @DisplayName("mapToResponseBodyList returns an empty list for no tags")
    void mapToResponseBodyListHandlesEmptyInput() {
        assertThat(tagService.mapToResponseBodyList(List.of())).isEmpty();
    }
}
