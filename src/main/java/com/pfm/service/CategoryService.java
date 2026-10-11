package com.pfm.service;

import com.pfm.dto.Dto.*;
import com.pfm.entity.Category;
import com.pfm.exception.ApiException;
import com.pfm.repository.CategoryRepository;
import com.pfm.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default and per-user custom categories. */
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public CategoryListResponse list(Long userId) {
        return new CategoryListResponse(
                categoryRepository.findVisible(userId).stream().map(CategoryResponse::from).toList());
    }

    /** Creates a custom category; names are unique per user and may not shadow a default. */
    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest req) {
        String name = req.name().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("name is required");
        }
        if (categoryRepository.findVisibleByName(name, userId).isPresent()) {
            throw ApiException.conflict("Category '" + name + "' already exists");
        }
        Category c = new Category();
        c.setName(name);
        c.setType(req.type());
        c.setOwnerId(userId);
        return CategoryResponse.from(categoryRepository.save(c));
    }

    /** Deletes a custom category that no transaction references. */
    @Transactional
    public MessageResponse delete(Long userId, String name) {
        Category c = categoryRepository.findVisibleByName(name.trim(), userId)
                .orElseThrow(() -> ApiException.notFound("Category '" + name + "' not found"));
        if (c.isDefault()) {
            throw ApiException.forbidden("Default categories cannot be deleted");
        }
        if (transactionRepository.existsByCategoryId(c.getId())) {
            throw ApiException.badRequest("Category is referenced by transactions and cannot be deleted");
        }
        categoryRepository.delete(c);
        return new MessageResponse("Category deleted successfully");
    }
}
