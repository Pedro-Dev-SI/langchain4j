package com.br.langchain4j.rental.application;

import com.br.langchain4j.rental.domain.RentalCategory;
import com.br.langchain4j.rental.repository.RentalCategoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class QuotationService {

    private final RentalCategoryRepository rentalCategoryRepository;

    public QuotationService(RentalCategoryRepository rentalCategoryRepository) {
        this.rentalCategoryRepository = rentalCategoryRepository;
    }

    public String calculateQuotation(
        String category,
        int days
    ) {
        if (category == null || category.isBlank()) {
            return "Informe uma categoria valida: economico, suv ou premium.";
        }

        if (days <= 0) {
            return "Informe uma quantidade de dias maior que zero.";
        }

        String normalizedCategory = category.trim().toLowerCase();

        RentalCategory rentalCategory = rentalCategoryRepository.findByCodeIgnoreCase(normalizedCategory)
                .orElseThrow(() -> new RentalCategoryNotFoundException(
                        "Categoria de codigo: " + category + " nao encontrada"
                ));

        BigDecimal base = rentalCategory.getDailyBasePrice();
        BigDecimal rate = rentalCategory.getInsuranceRate();

        BigDecimal subtotal = base.multiply(BigDecimal.valueOf(days));
        BigDecimal total = subtotal.multiply(BigDecimal.ONE.add(rate))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal insurancePercentage = rate.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP);

        return String.format(
                "Cotação: %s por %d dias -> R$ %s (inclui seguro %s%%)",
                normalizedCategory, days, formatCurrencyValue(total), insurancePercentage
        );
    }

    private String formatCurrencyValue(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP)
                .toPlainString()
                .replace(".", ",");
    }
}
