package com.scriptum.backend.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteRequestBody {

    @NotBlank(message = "Title is required")
    private String title;

    private String content;

    private String color;

    @JsonProperty("isPinned")
    private boolean pinned;

    @NotNull(message = "User ID is required")
    private UUID userId;

    private List<TagRef> tags = new ArrayList<>();

    public record TagRef(String name, String color) {}
}
