package com.pfm.controller;

import com.pfm.dto.Dto.*;
import com.pfm.security.CurrentUser;
import com.pfm.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Category endpoints (defaults + the caller's custom categories). */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;
    private final CurrentUser currentUser;

    @GetMapping
    public CategoryListResponse list() {
        return categoryService.list(currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(currentUser.id(), request);
    }

    @DeleteMapping("/{name}")
    public MessageResponse delete(@PathVariable String name) {
        return categoryService.delete(currentUser.id(), name);
    }
}
