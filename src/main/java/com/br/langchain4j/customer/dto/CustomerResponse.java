package com.br.langchain4j.customer.dto;

import java.util.UUID;

public record CustomerResponse(
        String name,
        String document,
        String email,
        String phone,
        String type
) {
}
