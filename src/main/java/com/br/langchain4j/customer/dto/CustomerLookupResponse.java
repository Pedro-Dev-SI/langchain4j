package com.br.langchain4j.customer.dto;

public record CustomerLookupResponse(
        boolean found,
        CustomerResponse customer,
        String message
) {
}
