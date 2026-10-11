package com.pfm.repository;

import com.pfm.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Category with this name (case-insensitive) that is a default or owned by the user. */
    @Query("select c from Category c where lower(c.name) = lower(:name) and (c.ownerId is null or c.ownerId = :userId)")
    Optional<Category> findVisibleByName(@Param("name") String name, @Param("userId") Long userId);

    /** All default categories plus the user's custom ones, defaults first (they are created first). */
    @Query("select c from Category c where c.ownerId is null or c.ownerId = :userId order by c.id")
    List<Category> findVisible(@Param("userId") Long userId);

    boolean existsByNameIgnoreCaseAndOwnerIdIsNull(String name);
}
