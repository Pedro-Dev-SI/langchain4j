package com.br.langchain4j.ai.tools;

import com.br.langchain4j.rental.application.CarServce;
import com.br.langchain4j.rental.application.QuotationService;
import com.br.langchain4j.rental.dto.AvailableCarResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssistantTools {

    private final QuotationService quotationService;
    private final CarServce carServce;

    public AssistantTools(QuotationService quotationService, CarServce carServce) {
        this.quotationService = quotationService;
        this.carServce = carServce;
    }

    @Tool("Calcula o valor total do aluguel corporativo com base na categoria do carro e número de dias.")
    public String calculateQuotation(
            @P("As categorias existentes: economico, suv e premium") String category,
            @P("Dias que o cliente deseja locar o carro: deve ser maior do que zero") int days
    ) {
        return quotationService.calculateQuotation(category, days);
    }
    @Tool("Retorna a lista de carros disponíveis para locação dada a categoria.")
    public List<AvailableCarResponse> checkAvailableCarsByCategory(
            @P("Codigo da categoria: economico, suv e premium") String category
    ) {
        return carServce.listAllAvailableCarsByCategory(category);
    }
}
