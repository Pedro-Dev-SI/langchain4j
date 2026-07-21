package com.br.langchain4j.rental.application;

import com.br.langchain4j.customer.application.CustomerPublicApi;
import com.br.langchain4j.customer.dto.CustomerLookupResponse;
import com.br.langchain4j.customer.dto.CustomerResponse;
import com.br.langchain4j.rental.domain.Car;
import com.br.langchain4j.rental.domain.Reservation;
import com.br.langchain4j.rental.dto.CreateReservationRequest;
import com.br.langchain4j.rental.dto.ReservationCompletedResponse;
import com.br.langchain4j.rental.dto.ReservationResponse;
import com.br.langchain4j.rental.domain.enums.StatusVeichleEnum;
import com.br.langchain4j.rental.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationService {


    private final ReservationRepository reservationRepository;
    private final CarServce carServce;
    private final CustomerPublicApi customerPublicApi;

    public ReservationService(
            ReservationRepository reservationRepository,
            CarServce carServce,
            CustomerPublicApi customerPublicApi
    ) {
        this.reservationRepository = reservationRepository;
        this.carServce = carServce;
        this.customerPublicApi = customerPublicApi;
    }

    @Transactional
    public ReservationCompletedResponse createReservation(CreateReservationRequest reservationRequest) {

        if (isInvalidRequest(reservationRequest)) {
            return new ReservationCompletedResponse(
                    false,
                    null,
                    "Dados da reserva são obrigatórios"
            );
        }

        var reservationOp = findReservationBySessionIdAndCarModelOptional(reservationRequest.sessionId(), reservationRequest.carModel());

        if (reservationOp.isPresent()) {
            return new ReservationCompletedResponse(
                    true,
                    toResponse(reservationOp.get()),
                    "Reserva já existe para esta sessão e veículo"
            );
        }

        if (reservationRequest.finishDate().isBefore(reservationRequest.startDate())
                || reservationRequest.finishDate().isEqual(reservationRequest.startDate())) {
            return new ReservationCompletedResponse(
                    false,
                    null,
                    "Data de entrega deve ser posterior à data de retirada"
            );
        }

        CustomerLookupResponse customerLookup = customerPublicApi.findByDocument(reservationRequest.document());

        if (!customerLookup.found()) {
            return new ReservationCompletedResponse(
                    false,
                    null,
                    customerLookup.message()
            );
        }

        CustomerResponse customer = customerLookup.customer();

        if (!carServce.checkAvailabilityByCarModel(reservationRequest.carModel())) {
            return new ReservationCompletedResponse(
                    false,
                    null,
                    "Veículo se encontra indisponível no momento"
            );
        }

        Car car = carServce.findCarByModel(reservationRequest.carModel());
        car.setStatus(StatusVeichleEnum.RESERVADO);

        Reservation reservation = new Reservation(
                car,
                customer.id(),
                reservationRequest.sessionId(),
                reservationRequest.startDate(),
                reservationRequest.finishDate()
        );

        Reservation savedReservation = reservationRepository.save(reservation);

        return new ReservationCompletedResponse(
                true,
                toResponse(savedReservation),
                "Reserva realizada com sucesso"
        );
    }

    private Optional<Reservation> findReservationBySessionIdAndCarModelOptional(UUID sessionId, String carModel) {

        Car car = carServce.findCarByModel(carModel);

        return reservationRepository.findBySessionIdAndCarId(sessionId, car.getId());

    }

    private ReservationResponse toResponse(Reservation reservation) {
        CustomerLookupResponse customerLookup = customerPublicApi.findById(reservation.getCustomerId());
        CustomerResponse customer = customerLookup.customer();

        if (!customerLookup.found() || customer == null) {
            return new ReservationResponse(
                reservation.getCar().getModel(),
                reservation.getCar().getCategory().getCode(),
                reservation.getCar().getPlate(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                "Cliente não encontrado",
                null,
                null
            );
        }

        return new ReservationResponse(
            reservation.getCar().getModel(),
            reservation.getCar().getCategory().getCode(),
            reservation.getCar().getPlate(),
            reservation.getStartDate(),
            reservation.getEndDate(),
            customer.name(),
            customer.document(),
            customer.phone()
        );
    }

    private boolean isInvalidRequest(CreateReservationRequest reservationRequest) {
        return reservationRequest == null
                || reservationRequest.sessionId() == null
                || reservationRequest.document() == null
                || reservationRequest.document().isBlank()
                || reservationRequest.startDate() == null
                || reservationRequest.finishDate() == null
                || reservationRequest.carModel() == null
                || reservationRequest.carModel().isBlank();
    }
}
