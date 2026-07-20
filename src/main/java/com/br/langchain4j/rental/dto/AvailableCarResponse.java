package com.br.langchain4j.rental.dto;

public record AvailableCarResponse(
        String model,
        String category,
        String status
) {
}
