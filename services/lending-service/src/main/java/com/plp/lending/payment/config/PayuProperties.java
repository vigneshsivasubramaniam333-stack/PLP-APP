package com.plp.lending.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "plp.payu")
public class PayuProperties {

    private String merchantKey = "";
    private String merchantSalt = "";
    private String gatewayUrl = "https://test.payu.in/_payment";
    private String verifyCommand = "verify_payment";
    private String postServiceUrl = "";
    private String transactionIdSuffix = "PLP";
    private String callbackBaseUrl = "http://localhost:8080";
    private String plpBorrowerUiUrl = "http://localhost:5174/plp-borrower";
    private String losBorrowerUiUrl = "http://localhost:5173/los/borrower";
}
