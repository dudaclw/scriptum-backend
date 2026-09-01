package com.scriptum.backend.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteResponseBody {

    private UUID id;

    private String title;

    private String content;

    private String color;

    @JsonProperty("isPinned")
    private boolean pinned;

    private UUID userId;

    private LocalDateTime createdAt;

    private LocalDateTime modifiedAt;

    private Set<TagResponseBody> tags = new HashSet<>();
}
