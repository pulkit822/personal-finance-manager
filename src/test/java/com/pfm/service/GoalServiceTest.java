package com.pfm.service;

import com.pfm.dto.Dto.*;
import com.pfm.entity.CategoryType;
import com.pfm.entity.Goal;
import com.pfm.exception.ApiException;
import com.pfm.repository.GoalRepository;
import com.pfm.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {
    @Mock GoalRepository goalRepository;
    @Mock TransactionRepository transactionRepository;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC);
    private GoalService service;

    @BeforeEach
    void setUp() {
        service = new GoalService(goalRepository, transactionRepository, clock);
    }

    private void stubSave() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(i -> {
            Goal g = i.getArgument(0);
            g.setId(1L);
            return g;
        });
    }

    private Goal goal(long id, long userId) {
        Goal g = new Goal();
        g.setId(id);
        g.setUserId(userId);
        g.setGoalName("Fund");
        g.setTargetAmount(new BigDecimal("10000.00"));
        g.setTargetDate(LocalDate.of(2027, 1, 1));
        g.setStartDate(LocalDate.of(2024, 1, 1));
        return g;
    }

    @Test
    void createComputesProgressFromIncomeMinusExpenses() {
        stubSave();
        when(transactionRepository.sumSince(eq(1L), eq(CategoryType.INCOME), any())).thenReturn(new BigDecimal("7000.00"));
        when(transactionRepository.sumSince(eq(1L), eq(CategoryType.EXPENSE), any())).thenReturn(new BigDecimal("450.00"));

        GoalResponse r = service.create(1L, new GoalRequest("Fund", new BigDecimal("10000.00"),
                LocalDate.of(2027, 1, 1), LocalDate.of(2024, 1, 1)));

        assertEquals(new BigDecimal("6550.00"), r.currentProgress());
        assertEquals(65.5, r.progressPercentage());
        assertEquals(new BigDecimal("3450.00"), r.remainingAmount());
    }

    @Test
    void createDefaultsStartDateToTodayAndReportsZeroProgress() {
        stubSave();
        GoalResponse r = service.create(1L, new GoalRequest("Fund", new BigDecimal("5000"), LocalDate.of(2027, 1, 1), null));
        assertEquals(LocalDate.of(2026, 10, 10), r.startDate());
        assertEquals(BigDecimal.ZERO, r.currentProgress());
        assertEquals(0.0, r.progressPercentage());
    }

    @Test
    void createRejectsPastTargetDate() {
        ApiException ex = assertThrows(ApiException.class, () -> service.create(1L,
                new GoalRequest("Fund", BigDecimal.TEN, LocalDate.of(2026, 10, 10), null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createRejectsStartAfterTarget() {
        ApiException ex = assertThrows(ApiException.class, () -> service.create(1L,
                new GoalRequest("Fund", BigDecimal.TEN, LocalDate.of(2027, 1, 1), LocalDate.of(2028, 1, 1))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void getOfAnotherUsersGoalIsForbidden() {
        when(goalRepository.findById(5L)).thenReturn(Optional.of(goal(5, 2)));
        ApiException ex = assertThrows(ApiException.class, () -> service.get(1L, 5L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void getMissingGoalIsNotFound() {
        when(goalRepository.findById(9L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.delete(1L, 9L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void updateChangesTargetAmountAndRecomputesPercentage() {
        Goal g = goal(1, 1);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(g));
        when(goalRepository.save(g)).thenReturn(g);
        when(transactionRepository.sumSince(eq(1L), eq(CategoryType.INCOME), any())).thenReturn(new BigDecimal("10000.00"));
        when(transactionRepository.sumSince(eq(1L), eq(CategoryType.EXPENSE), any())).thenReturn(new BigDecimal("950.00"));

        GoalResponse r = service.update(1L, 1L, new GoalUpdateRequest(new BigDecimal("15000"), null));

        assertEquals(60.33, r.progressPercentage());
        assertEquals(new BigDecimal("5950.00"), r.remainingAmount());
    }

    @Test
    void updateRequiresAtLeastOneField() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1, 1)));
        assertThrows(ApiException.class, () -> service.update(1L, 1L, new GoalUpdateRequest(null, null)));
    }

    @Test
    void updateRejectsPastTargetDate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1, 1)));
        assertThrows(ApiException.class,
                () -> service.update(1L, 1L, new GoalUpdateRequest(null, LocalDate.of(2020, 1, 1))));
    }

    @Test
    void percentageIsClampedAndRemainingNeverNegative() {
        Goal g = goal(1, 1);
        when(goalRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(g));
        when(transactionRepository.sumSince(eq(1L), eq(CategoryType.INCOME), any())).thenReturn(new BigDecimal("20000.00"));

        GoalResponse r = service.list(1L).goals().get(0);

        assertEquals(100.0, r.progressPercentage());
        assertEquals(BigDecimal.ZERO, r.remainingAmount());
    }

    @Test
    void deleteRemovesOwnedGoal() {
        Goal g = goal(1, 1);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(g));
        assertEquals("Goal deleted successfully", service.delete(1L, 1L).message());
        verify(goalRepository).delete(g);
    }
}
