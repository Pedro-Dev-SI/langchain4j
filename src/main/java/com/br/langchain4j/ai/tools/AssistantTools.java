package com.br.langchain4j.ai.tools;

import com.br.langchain4j.rental.application.QuotationService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class AssistantTools {

    private final QuotationService quotationService;

    public AssistantTools(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    @Tool("Calcula o valor total do aluguel corporativo com base na categoria do carro e número de dias.")
    public String calculateQuotation(
            @P("As categorias existentes: economico, suv e premium") String category,
            @P("Dias que o cliente deseja locar o carro: deve ser maior do que zero") int days
    ) {
        return quotationService.calculateQuotation(category, days);
    }
}
