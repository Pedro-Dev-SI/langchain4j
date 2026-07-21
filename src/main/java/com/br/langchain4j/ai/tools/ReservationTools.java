package com.br.langchain4j.ai.tools;

import com.br.langchain4j.rental.application.ReservationService;
import com.br.langchain4j.rental.dto.CreateReservationRequest;
import com.br.langchain4j.rental.dto.ReservationCompletedResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ReservationTools {

    private final ReservationService reservationService;

    public ReservationTools(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Tool("Realiza uma nova reserva de um carro escolhido pelo cliente")
    public ReservationCompletedResponse createNewReservation(
            @ToolMemoryId UUID sessionId,
            @P("CPF do cliente") String document,
            @P("Data de retirada do veículo, início da locação") LocalDateTime startDate,
            @P("Data para a entrega do veículo, fim da locação") LocalDateTime endDate,
            @P("Modelo do carro escolhido") String carModel
    ){
        CreateReservationRequest reservationRequest = new CreateReservationRequest(
                sessionId,
                document,
                startDate,
                endDate,
                carModel
        );
        return reservationService.createReservation(reservationRequest);
    }
}
