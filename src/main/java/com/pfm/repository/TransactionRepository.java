package com.pfm.repository;

import com.pfm.entity.CategoryType;
import com.pfm.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    boolean existsByCategoryId(Long categoryId);

    List<Transaction> findByUserIdAndTransactionDateBetween(Long userId, LocalDate start, LocalDate end);

    /** Sum of the user's transactions of one type on/after {@code start}; null when there are none. */
    @Query("select sum(t.amount) from Transaction t where t.userId = :userId and t.category.type = :type and t.transactionDate >= :start")
    BigDecimal sumSince(@Param("userId") Long userId, @Param("type") CategoryType type, @Param("start") LocalDate start);
}
