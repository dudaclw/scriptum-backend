package com.scriptum.backend.infrastructure.database.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "NOTES")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notes extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID", nullable = false, updatable = false, unique = true)
    private UUID id;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "CONTENT", columnDefinition = "TEXT")
    private String content;

    @Column(name = "PINNED")
    private boolean pinned;

    @Column(name = "COLOR")
    private String color;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false, updatable = false)
    private UserJpaEntity user;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "NOTE_TAGS",
        joinColumns = @JoinColumn(name = "NOTE_ID"),
        inverseJoinColumns = @JoinColumn(name = "TAG_ID")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();
}
