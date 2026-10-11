package com.pfm.config;

import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;
import com.pfm.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds the immutable default categories on startup (idempotent). */
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("Salary", CategoryType.INCOME);
        for (String n : new String[]{"Food", "Rent", "Transportation", "Entertainment", "Healthcare", "Utilities"}) {
            seed(n, CategoryType.EXPENSE);
        }
    }

    private void seed(String name, CategoryType type) {
        if (!categoryRepository.existsByNameIgnoreCaseAndOwnerIdIsNull(name)) {
            Category c = new Category();
            c.setName(name);
            c.setType(type);
            categoryRepository.save(c);
        }
    }
}
