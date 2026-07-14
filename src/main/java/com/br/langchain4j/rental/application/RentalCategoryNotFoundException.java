package com.br.langchain4j.rental.application;

public class RentalCategoryNotFoundException extends RuntimeException {

    public RentalCategoryNotFoundException(String message) {
        super(message);
    }
}
