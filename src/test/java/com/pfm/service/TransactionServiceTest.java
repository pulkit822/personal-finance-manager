package com.pfm.service;

import com.pfm.dto.Dto.*;
import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;
import com.pfm.entity.Transaction;
import com.pfm.exception.ApiException;
import com.pfm.repository.CategoryRepository;
import com.pfm.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock TransactionRepository transactionRepository;
    @Mock CategoryRepository categoryRepository;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC);
    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, categoryRepository, clock);
    }

    private Category category(String name, CategoryType type) {
        Category c = new Category();
        c.setId(1L);
        c.setName(name);
        c.setType(type);
        return c;
    }

    private Transaction txn(Category c) {
        Transaction t = new Transaction();
        t.setId(7L);
        t.setUserId(1L);
        t.setAmount(new BigDecimal("100.00"));
        t.setTransactionDate(LocalDate.of(2024, 1, 15));
        t.setCategory(c);
        t.setDescription("old");
        return t;
    }

    @Test
    void createSavesTransactionAndDerivesType() {
        when(categoryRepository.findVisibleByName("Salary", 1L)).thenReturn(Optional.of(category("Salary", CategoryType.INCOME)));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(10L);
            return t;
        });

        TransactionResponse r = service.create(1L, new TransactionRequest(new BigDecimal("5000"),
                LocalDate.of(2024, 1, 15), " Salary ", "January"));

        assertEquals(10L, r.id());
        assertEquals(new BigDecimal("5000.00"), r.amount());
        assertEquals(CategoryType.INCOME, r.type());
    }

    @Test
    void createRejectsFutureDate() {
        assertThrows(ApiException.class, () -> service.create(1L,
                new TransactionRequest(BigDecimal.TEN, LocalDate.of(2026, 10, 11), "Food", null)));
    }

    @Test
    void createRejectsUnknownCategory() {
        when(categoryRepository.findVisibleByName(anyString(), eq(1L))).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.create(1L,
                new TransactionRequest(BigDecimal.TEN, LocalDate.of(2024, 1, 1), "Nope", null)));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void updateChangesFieldsButNeverTheDate() {
        Transaction t = txn(category("Salary", CategoryType.INCOME));
        when(transactionRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(t));
        when(transactionRepository.save(t)).thenReturn(t);

        TransactionResponse r = service.update(1L, 7L, new TransactionUpdateRequest(new BigDecimal("5500"), null, "new"));

        assertEquals(LocalDate.of(2024, 1, 15), r.date());
        assertEquals(new BigDecimal("5500.00"), r.amount());
        assertEquals("new", r.description());
    }

    @Test
    void updateCanChangeCategory() {
        Transaction t = txn(category("Salary", CategoryType.INCOME));
        when(transactionRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(t));
        when(categoryRepository.findVisibleByName("Food", 1L)).thenReturn(Optional.of(category("Food", CategoryType.EXPENSE)));
        when(transactionRepository.save(t)).thenReturn(t);

        assertEquals(CategoryType.EXPENSE, service.update(1L, 7L, new TransactionUpdateRequest(null, "Food", null)).type());
    }

    @Test
    void updateMissingTransactionIsNotFound() {
        when(transactionRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class,
                () -> service.update(1L, 9L, new TransactionUpdateRequest(null, null, null)));
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void deleteRemovesTransaction() {
        Transaction t = txn(category("Food", CategoryType.EXPENSE));
        when(transactionRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(t));
        assertEquals("Transaction deleted successfully", service.delete(1L, 7L).message());
        verify(transactionRepository).delete(t);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAppliesFiltersAndMapsResults() {
        when(transactionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(txn(category("Salary", CategoryType.INCOME))));
        // Run the specification lambda against nothing: only wiring is verified here (SQL is covered by the integration test).
        TransactionListResponse r = service.list(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), null, null, "income");
        assertEquals(1, r.transactions().size());
    }

    @Test
    void listRejectsInvertedRangeAndBadType() {
        assertThrows(ApiException.class, () -> service.list(1L, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1), null, null, null));
        assertThrows(ApiException.class, () -> service.list(1L, null, null, null, null, "bogus"));
    }
}
