package com.pfm.service;

import com.pfm.dto.Dto.*;
import com.pfm.entity.CategoryType;
import com.pfm.entity.Transaction;
import com.pfm.exception.ApiException;
import com.pfm.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.pfm.util.Money.fmt;

/** Monthly and yearly income/expense summaries (deleted transactions are simply gone). */
@Service
@RequiredArgsConstructor
public class ReportService {
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public MonthlyReport monthly(Long userId, int year, int month) {
        validateYear(year);
        if (month < 1 || month > 12) {
            throw ApiException.badRequest("month must be between 1 and 12");
        }
        YearMonth ym = YearMonth.of(year, month);
        Totals t = aggregate(transactionRepository.findByUserIdAndTransactionDateBetween(userId, ym.atDay(1), ym.atEndOfMonth()));
        return new MonthlyReport(month, year, t.income, t.expenses, t.net());
    }

    @Transactional(readOnly = true)
    public YearlyReport yearly(Long userId, int year) {
        validateYear(year);
        Totals t = aggregate(transactionRepository.findByUserIdAndTransactionDateBetween(
                userId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));
        return new YearlyReport(year, t.income, t.expenses, t.net());
    }

    private void validateYear(int year) {
        if (year < 1900 || year > 9999) {
            throw ApiException.badRequest("year must be between 1900 and 9999");
        }
    }

    private Totals aggregate(List<Transaction> txns) {
        Totals t = new Totals();
        for (Transaction x : txns) {
            Map<String, BigDecimal> target = x.getCategory().getType() == CategoryType.INCOME ? t.income : t.expenses;
            target.merge(x.getCategory().getName(), x.getAmount(), BigDecimal::add);
        }
        t.income.replaceAll((k, v) -> fmt(v));
        t.expenses.replaceAll((k, v) -> fmt(v));
        return t;
    }

    private static final class Totals {
        final Map<String, BigDecimal> income = new TreeMap<>();
        final Map<String, BigDecimal> expenses = new TreeMap<>();

        BigDecimal net() {
            BigDecimal in = income.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal out = expenses.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return fmt(in.subtract(out));
        }
    }
}
