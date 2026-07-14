package com.br.langchain4j.ai.api;

import com.br.langchain4j.ai.application.AssistantAiService;
import com.br.langchain4j.ai.dto.AssistantRequest;
import com.br.langchain4j.ai.dto.AssistantResponse;
import dev.langchain4j.service.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/assistant")
public class AssistantAiController {

    private final AssistantAiService assistantAiService;

    public AssistantAiController(AssistantAiService assistantAiService) {
        this.assistantAiService = assistantAiService;
    }

    @PostMapping
    public AssistantResponse askAssistant(@Valid @RequestBody AssistantRequest request) {

        UUID sessionId = request.sessionId() != null
                ? request.sessionId()
                : UUID.randomUUID();

        Result<String> result = assistantAiService.handleRequest(sessionId, request.message());
        return new AssistantResponse(sessionId, result.content());
    }
}
