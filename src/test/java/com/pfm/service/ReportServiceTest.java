package com.pfm.service;

import com.pfm.dto.Dto.*;
import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;
import com.pfm.entity.Transaction;
import com.pfm.exception.ApiException;
import com.pfm.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock TransactionRepository transactionRepository;
    @InjectMocks ReportService service;

    private Transaction txn(String cat, CategoryType type, String amount) {
        Category c = new Category();
        c.setName(cat);
        c.setType(type);
        Transaction t = new Transaction();
        t.setCategory(c);
        t.setAmount(new BigDecimal(amount));
        return t;
    }

    @Test
    void monthlyAggregatesByCategoryAndComputesNet() {
        when(transactionRepository.findByUserIdAndTransactionDateBetween(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(List.of(txn("Salary", CategoryType.INCOME, "5500.00"), txn("Freelance", CategoryType.INCOME, "1500.00"),
                        txn("Food", CategoryType.EXPENSE, "200.00"), txn("Food", CategoryType.EXPENSE, "250.00")));

        MonthlyReport r = service.monthly(1L, 2024, 1);

        assertEquals(new BigDecimal("5500.00"), r.totalIncome().get("Salary"));
        assertEquals(new BigDecimal("450.00"), r.totalExpenses().get("Food"));
        assertEquals(new BigDecimal("6550.00"), r.netSavings());
        assertEquals(1, r.month());
    }

    @Test
    void emptyPeriodGivesZeroNet() {
        when(transactionRepository.findByUserIdAndTransactionDateBetween(1L, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)))
                .thenReturn(List.of());
        YearlyReport r = service.yearly(1L, 2023);
        assertEquals(BigDecimal.ZERO, r.netSavings());
        assertTrue(r.totalIncome().isEmpty());
    }

    @Test
    void invalidMonthAndYearAreRejected() {
        assertThrows(ApiException.class, () -> service.monthly(1L, 2024, 13));
        assertThrows(ApiException.class, () -> service.monthly(1L, 2024, 0));
        assertThrows(ApiException.class, () -> service.yearly(1L, 12));
    }
}
