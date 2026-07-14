package com.br.langchain4j.ai.api;

import java.util.List;

public record ApiErrorResponse(
        int status,
        String error,
        List<String> messages
) {
}
