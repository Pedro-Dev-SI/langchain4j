package com.br.langchain4j.rental.repository;

import com.br.langchain4j.rental.domain.Car;
import com.br.langchain4j.rental.domain.enums.StatusVeichleEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CarRepository extends JpaRepository<Car, UUID> {

    List<Car> findAllByCategoryCodeAndStatus(String code, StatusVeichleEnum status);
}
