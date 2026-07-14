package com.br.langchain4j.knowledge.dto;

public record DocumentDefinition(
        String path,
        String source,
        String category,
        String title
) {
}
