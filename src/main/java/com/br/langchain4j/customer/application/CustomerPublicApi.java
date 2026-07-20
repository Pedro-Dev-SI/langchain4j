package com.br.langchain4j.customer.application;

import com.br.langchain4j.customer.dto.CreateCustomerRequest;
import com.br.langchain4j.customer.dto.CustomerLookupResponse;
import com.br.langchain4j.customer.dto.CustomerResponse;

import java.util.Optional;

public interface CustomerPublicApi {

    CustomerLookupResponse createNewCustomer(CreateCustomerRequest request);

    CustomerLookupResponse findByDocument(String document);
}
