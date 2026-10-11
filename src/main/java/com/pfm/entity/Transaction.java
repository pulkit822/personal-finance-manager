package com.pfm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A single income or expense entry belonging to one user. */
@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor
public class Transaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "txn_date", nullable = false)
    private LocalDate transactionDate;
    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id")
    private Category category;
    @Column(length = 500)
    private String description;
}
