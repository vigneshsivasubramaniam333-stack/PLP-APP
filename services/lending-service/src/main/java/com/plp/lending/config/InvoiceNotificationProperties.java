package com.plp.lending.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "plp.notification")
public class InvoiceNotificationProperties {

    /** Base URL of the borrower portal (no trailing slash), e.g. http://localhost:5174/plp-borrower */
    private String borrowerPortalUrl = "http://localhost:5174/plp-borrower";

    public String invoicePaymentPath() {
        return borrowerPortalUrl.replaceAll("/$", "") + "/invoice-discounting";
    }
}
