package com.br.langchain4j.rental.repository;

import com.br.langchain4j.rental.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findBySessionIdAndCarId(UUID sessionId, UUID carId);

    Optional<Reservation> findByCustomerId(UUID id);
}
