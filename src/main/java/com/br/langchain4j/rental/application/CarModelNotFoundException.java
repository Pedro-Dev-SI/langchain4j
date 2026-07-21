package com.br.langchain4j.rental.application;

public class CarModelNotFoundException extends RuntimeException {

    public CarModelNotFoundException(String message) {
        super(message);
    }
}
