package com.br.langchain4j.ai.dto;

import java.util.UUID;

public record AssistantResponse(UUID sessionId, String answer) {
}
