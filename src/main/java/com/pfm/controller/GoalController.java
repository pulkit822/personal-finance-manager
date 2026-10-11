package com.pfm.controller;

import com.pfm.dto.Dto.*;
import com.pfm.security.CurrentUser;
import com.pfm.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Savings goal endpoints. */
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {
    private final GoalService goalService;
    private final CurrentUser currentUser;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody GoalRequest request) {
        return goalService.create(currentUser.id(), request);
    }

    @GetMapping
    public GoalListResponse list() {
        return goalService.list(currentUser.id());
    }

    @GetMapping("/{id}")
    public GoalResponse get(@PathVariable Long id) {
        return goalService.get(currentUser.id(), id);
    }

    @PutMapping("/{id}")
    public GoalResponse update(@PathVariable Long id, @Valid @RequestBody GoalUpdateRequest request) {
        return goalService.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable Long id) {
        return goalService.delete(currentUser.id(), id);
    }
}
