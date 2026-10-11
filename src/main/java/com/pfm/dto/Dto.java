package com.pfm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;
import com.pfm.entity.Transaction;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.pfm.util.Money.fmt;

/** All request/response objects, grouped to keep the package small. Entities are never exposed. */
public final class Dto {
    private Dto() { }

    // ---------- auth ----------
    public record RegisterRequest(
            @NotBlank(message = "username is required") @Email(message = "username must be a valid email address") String username,
            @NotBlank(message = "password is required") @Size(min = 8, max = 100, message = "password must be 8-100 characters") String password,
            @NotBlank(message = "fullName is required") @Size(max = 100, message = "fullName must be at most 100 characters") String fullName,
            @NotBlank(message = "phoneNumber is required")
            @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "phoneNumber must be a valid phone number (7-15 digits, optional leading +)") String phoneNumber) { }

    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password) { }

    public record MessageResponse(String message) { }

    public record RegisterResponse(String message, Long userId) { }

    // ---------- transactions ----------
    public record TransactionRequest(
            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than 0")
            @Digits(integer = 15, fraction = 2, message = "amount must have at most 2 decimal places") BigDecimal amount,
            @NotNull(message = "date is required") LocalDate date,
            @NotBlank(message = "category is required") String category,
            @Size(max = 500, message = "description must be at most 500 characters") String description) { }

    /** Partial update. A {@code date} in the body is intentionally not bound: dates are immutable. */
    public record TransactionUpdateRequest(
            @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than 0")
            @Digits(integer = 15, fraction = 2, message = "amount must have at most 2 decimal places") BigDecimal amount,
            String category,
            @Size(max = 500, message = "description must be at most 500 characters") String description) { }

    public record TransactionResponse(Long id, BigDecimal amount, LocalDate date, String category,
                                      String description, CategoryType type) {
        public static TransactionResponse from(Transaction t) {
            return new TransactionResponse(t.getId(), fmt(t.getAmount()), t.getTransactionDate(),
                    t.getCategory().getName(), t.getDescription(), t.getCategory().getType());
        }
    }

    public record TransactionListResponse(List<TransactionResponse> transactions) { }

    // ---------- categories ----------
    public record CategoryRequest(
            @NotBlank(message = "name is required") @Size(max = 50, message = "name must be at most 50 characters") String name,
            @NotNull(message = "type is required (INCOME or EXPENSE)") CategoryType type) { }

    /** Emits both {@code isCustom} (per the API spec) and {@code custom} (what the test script reads). */
    public record CategoryResponse(String name, CategoryType type,
                                   @JsonProperty("isCustom") boolean customFlag,
                                   @JsonProperty("custom") boolean customAlias) {
        public static CategoryResponse from(Category c) {
            boolean custom = !c.isDefault();
            return new CategoryResponse(c.getName(), c.getType(), custom, custom);
        }
    }

    public record CategoryListResponse(List<CategoryResponse> categories) { }

    // ---------- goals ----------
    public record GoalRequest(
            @NotBlank(message = "goalName is required") @Size(max = 100, message = "goalName must be at most 100 characters") String goalName,
            @NotNull(message = "targetAmount is required")
            @DecimalMin(value = "0.0", inclusive = false, message = "targetAmount must be greater than 0")
            @Digits(integer = 15, fraction = 2, message = "targetAmount must have at most 2 decimal places") BigDecimal targetAmount,
            @NotNull(message = "targetDate is required") LocalDate targetDate,
            LocalDate startDate) { }

    public record GoalUpdateRequest(
            @DecimalMin(value = "0.0", inclusive = false, message = "targetAmount must be greater than 0")
            @Digits(integer = 15, fraction = 2, message = "targetAmount must have at most 2 decimal places") BigDecimal targetAmount,
            LocalDate targetDate) { }

    public record GoalResponse(Long id, String goalName, BigDecimal targetAmount, LocalDate targetDate,
                               LocalDate startDate, BigDecimal currentProgress, double progressPercentage,
                               BigDecimal remainingAmount) { }

    public record GoalListResponse(List<GoalResponse> goals) { }

    // ---------- reports ----------
    public record MonthlyReport(int month, int year, Map<String, BigDecimal> totalIncome,
                                Map<String, BigDecimal> totalExpenses, BigDecimal netSavings) { }

    public record YearlyReport(int year, Map<String, BigDecimal> totalIncome,
                               Map<String, BigDecimal> totalExpenses, BigDecimal netSavings) { }

    // ---------- errors ----------
    public record ApiError(int status, String error, String message, Instant timestamp) { }
}
