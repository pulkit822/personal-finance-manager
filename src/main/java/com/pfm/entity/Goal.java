package com.pfm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A savings goal; progress is derived from transactions, never stored. */
@Entity
@Table(name = "goals")
@Getter @Setter @NoArgsConstructor
public class Goal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String goalName;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;
    @Column(nullable = false)
    private LocalDate targetDate;
    @Column(nullable = false)
    private LocalDate startDate;
}
