package com.br.langchain4j.rental.dto;

public record ReservationCompletedResponse(
        Boolean success,
        ReservationResponse reservation,
        String message
) {
}
