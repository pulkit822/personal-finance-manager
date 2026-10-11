package com.pfm.service;

import com.pfm.dto.Dto.*;
import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;
import com.pfm.entity.Transaction;
import com.pfm.exception.ApiException;
import com.pfm.repository.CategoryRepository;
import com.pfm.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Transaction CRUD scoped to the calling user. */
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest req) {
        if (req.date().isAfter(LocalDate.now(clock))) {
            throw ApiException.badRequest("date cannot be in the future");
        }
        Category category = resolveCategory(userId, req.category());
        Transaction t = new Transaction();
        t.setUserId(userId);
        t.setAmount(req.amount().setScale(2, RoundingMode.UNNECESSARY));
        t.setTransactionDate(req.date());
        t.setCategory(category);
        t.setDescription(req.description());
        return TransactionResponse.from(transactionRepository.save(t));
    }

    /** Lists the user's transactions, newest first, with optional filters. */
    @Transactional(readOnly = true)
    public TransactionListResponse list(Long userId, LocalDate startDate, LocalDate endDate,
                                        String categoryName, Long categoryId, String type) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw ApiException.badRequest("startDate must not be after endDate");
        }
        CategoryType categoryType = parseType(type);
        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("userId"), userId));
            if (startDate != null) p.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            if (endDate != null) p.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            if (categoryName != null && !categoryName.isBlank()) {
                p.add(cb.equal(cb.lower(root.get("category").get("name")), categoryName.trim().toLowerCase(Locale.ROOT)));
            }
            if (categoryId != null) p.add(cb.equal(root.get("category").get("id"), categoryId));
            if (categoryType != null) p.add(cb.equal(root.get("category").get("type"), categoryType));
            return cb.and(p.toArray(new Predicate[0]));
        };
        List<Transaction> rows = transactionRepository.findAll(spec,
                Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("id")));
        return new TransactionListResponse(rows.stream().map(TransactionResponse::from).toList());
    }

    /** Partial update of amount/category/description; the date can never change. */
    @Transactional
    public TransactionResponse update(Long userId, Long id, TransactionUpdateRequest req) {
        Transaction t = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Transaction not found with id " + id));
        if (req.amount() != null) {
            t.setAmount(req.amount().setScale(2, RoundingMode.UNNECESSARY));
        }
        if (req.category() != null) {
            t.setCategory(resolveCategory(userId, req.category()));
        }
        if (req.description() != null) {
            t.setDescription(req.description());
        }
        return TransactionResponse.from(transactionRepository.save(t));
    }

    @Transactional
    public MessageResponse delete(Long userId, Long id) {
        Transaction t = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Transaction not found with id " + id));
        transactionRepository.delete(t);
        return new MessageResponse("Transaction deleted successfully");
    }

    private Category resolveCategory(Long userId, String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("category is required");
        }
        return categoryRepository.findVisibleByName(name.trim(), userId)
                .orElseThrow(() -> ApiException.badRequest("Category '" + name.trim() + "' does not exist"));
    }

    private CategoryType parseType(String type) {
        if (type == null || type.isBlank()) return null;
        try {
            return CategoryType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("type must be INCOME or EXPENSE");
        }
    }
}
