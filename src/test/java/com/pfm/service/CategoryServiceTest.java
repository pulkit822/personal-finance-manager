package com.pfm.service;

import com.pfm.dto.Dto.*;
import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;
import com.pfm.exception.ApiException;
import com.pfm.repository.CategoryRepository;
import com.pfm.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock CategoryRepository categoryRepository;
    @Mock TransactionRepository transactionRepository;
    @InjectMocks CategoryService service;

    private Category cat(Long id, String name, Long owner) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setType(CategoryType.EXPENSE);
        c.setOwnerId(owner);
        return c;
    }

    @Test
    void listMarksCustomCategories() {
        when(categoryRepository.findVisible(1L)).thenReturn(List.of(cat(1L, "Food", null), cat(2L, "Gym", 1L)));
        List<CategoryResponse> r = service.list(1L).categories();
        assertFalse(r.get(0).customFlag());
        assertTrue(r.get(1).customFlag());
        assertTrue(r.get(1).customAlias());
    }

    @Test
    void createRejectsDuplicateName() {
        when(categoryRepository.findVisibleByName("Food", 1L)).thenReturn(Optional.of(cat(1L, "Food", null)));
        ApiException ex = assertThrows(ApiException.class, () -> service.create(1L, new CategoryRequest("Food", CategoryType.EXPENSE)));
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void createSavesCustomCategoryForUser() {
        when(categoryRepository.findVisibleByName("Gym", 1L)).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
        CategoryResponse r = service.create(1L, new CategoryRequest(" Gym ", CategoryType.EXPENSE));
        assertEquals("Gym", r.name());
        assertTrue(r.customFlag());
    }

    @Test
    void createRejectsBlankName() {
        assertThrows(ApiException.class, () -> service.create(1L, new CategoryRequest("   ", CategoryType.INCOME)));
    }

    @Test
    void deleteDefaultIsForbidden() {
        when(categoryRepository.findVisibleByName("Food", 1L)).thenReturn(Optional.of(cat(1L, "Food", null)));
        assertEquals(403, assertThrows(ApiException.class, () -> service.delete(1L, "Food")).getStatus().value());
    }

    @Test
    void deleteInUseIsBadRequest() {
        when(categoryRepository.findVisibleByName("Gym", 1L)).thenReturn(Optional.of(cat(2L, "Gym", 1L)));
        when(transactionRepository.existsByCategoryId(2L)).thenReturn(true);
        assertEquals(400, assertThrows(ApiException.class, () -> service.delete(1L, "Gym")).getStatus().value());
    }

    @Test
    void deleteMissingIsNotFound() {
        when(categoryRepository.findVisibleByName("X", 1L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ApiException.class, () -> service.delete(1L, "X")).getStatus().value());
    }

    @Test
    void deleteUnusedCustomCategorySucceeds() {
        Category c = cat(2L, "Gym", 1L);
        when(categoryRepository.findVisibleByName("Gym", 1L)).thenReturn(Optional.of(c));
        when(transactionRepository.existsByCategoryId(2L)).thenReturn(false);
        assertEquals("Category deleted successfully", service.delete(1L, "Gym").message());
        verify(categoryRepository).delete(c);
    }
}
