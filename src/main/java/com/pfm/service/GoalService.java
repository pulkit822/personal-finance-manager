package com.pfm.service;

import com.pfm.dto.Dto.*;
import com.pfm.entity.CategoryType;
import com.pfm.entity.Goal;
import com.pfm.exception.ApiException;
import com.pfm.repository.GoalRepository;
import com.pfm.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;

import static com.pfm.util.Money.fmt;
import static com.pfm.util.Money.nz;

/** Savings goals. Progress = (income - expenses) since the goal's start date, computed live. */
@Service
@RequiredArgsConstructor
public class GoalService {
    private final GoalRepository goalRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Transactional
    public GoalResponse create(Long userId, GoalRequest req) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = req.startDate() != null ? req.startDate() : today;
        validateDates(start, req.targetDate(), today);
        Goal g = new Goal();
        g.setUserId(userId);
        g.setGoalName(req.goalName().trim());
        g.setTargetAmount(req.targetAmount().setScale(2, RoundingMode.UNNECESSARY));
        g.setTargetDate(req.targetDate());
        g.setStartDate(start);
        return toResponse(goalRepository.save(g));
    }

    @Transactional(readOnly = true)
    public GoalListResponse list(Long userId) {
        return new GoalListResponse(goalRepository.findByUserIdOrderByIdAsc(userId).stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public GoalResponse get(Long userId, Long id) {
        return toResponse(findOwned(userId, id));
    }

    @Transactional
    public GoalResponse update(Long userId, Long id, GoalUpdateRequest req) {
        Goal g = findOwned(userId, id);
        if (req.targetAmount() == null && req.targetDate() == null) {
            throw ApiException.badRequest("Provide targetAmount and/or targetDate to update");
        }
        if (req.targetDate() != null) {
            validateDates(g.getStartDate(), req.targetDate(), LocalDate.now(clock));
            g.setTargetDate(req.targetDate());
        }
        if (req.targetAmount() != null) {
            g.setTargetAmount(req.targetAmount().setScale(2, RoundingMode.UNNECESSARY));
        }
        return toResponse(goalRepository.save(g));
    }

    @Transactional
    public MessageResponse delete(Long userId, Long id) {
        goalRepository.delete(findOwned(userId, id));
        return new MessageResponse("Goal deleted successfully");
    }

    private Goal findOwned(Long userId, Long id) {
        Goal g = goalRepository.findById(id).orElseThrow(() -> ApiException.notFound("Goal not found with id " + id));
        if (!g.getUserId().equals(userId)) {
            throw ApiException.forbidden("You do not have access to this goal");
        }
        return g;
    }

    private void validateDates(LocalDate start, LocalDate target, LocalDate today) {
        if (!target.isAfter(today)) {
            throw ApiException.badRequest("targetDate must be a future date");
        }
        if (start.isAfter(target)) {
            throw ApiException.badRequest("startDate must not be after targetDate");
        }
    }

    private GoalResponse toResponse(Goal g) {
        BigDecimal income = nz(transactionRepository.sumSince(g.getUserId(), CategoryType.INCOME, g.getStartDate()));
        BigDecimal expense = nz(transactionRepository.sumSince(g.getUserId(), CategoryType.EXPENSE, g.getStartDate()));
        BigDecimal progress = income.subtract(expense);
        BigDecimal pct = progress.multiply(BigDecimal.valueOf(100))
                .divide(g.getTargetAmount(), 2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        BigDecimal remaining = g.getTargetAmount().subtract(progress).max(BigDecimal.ZERO);
        return new GoalResponse(g.getId(), g.getGoalName(), fmt(g.getTargetAmount()), g.getTargetDate(),
                g.getStartDate(), fmt(progress), pct.doubleValue(), fmt(remaining));
    }
}
