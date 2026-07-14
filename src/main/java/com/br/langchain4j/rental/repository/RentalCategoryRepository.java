package com.br.langchain4j.rental.repository;

import com.br.langchain4j.rental.domain.RentalCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RentalCategoryRepository extends JpaRepository<RentalCategory, Long> {

    Optional<RentalCategory> findByCodeIgnoreCase(String code);

    List<RentalCategory> findByActiveTrueOrderByNameAsc();
}
