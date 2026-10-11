package com.pfm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Transaction category. {@code ownerId == null} marks a built-in default category. */
@Entity
@Table(name = "categories")
@Getter @Setter @NoArgsConstructor
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private CategoryType type;
    /** Owning user id; {@code null} for default categories. */
    private Long ownerId;

    public boolean isDefault() { return ownerId == null; }
}
