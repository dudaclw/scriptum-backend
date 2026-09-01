package com.scriptum.backend.domain.entities;

import com.scriptum.backend.infrastructure.database.jpa.UserJpaEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Note {

    private UUID id;
    
    private String title;
    
    private String content;

    private boolean pinned;

    private String color;

    private UUID userId;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime modifiedAt;
    
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

}
