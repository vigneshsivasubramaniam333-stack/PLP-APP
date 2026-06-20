package com.plp.iam.dev;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class DevPreservedUserEmails {

    private final List<String> preservedEmailsLower;

    public DevPreservedUserEmails(
            @Value("${plp.dev-reset.preserved-user-emails:admin@plp.com,anchor@testcorp.com,raj@testcorp.com,priya@buyerco.com,admin@credinnov.com,creditmanager@credinnov.com,creditofficer@credinnov.com,creditofficer2@credinnov.com,sales@credinnov.com,accounts@credinnov.com,anchor@credinnov.com,borrower@credinnov.com}") String csv) {
        this.preservedEmailsLower = parseCsv(csv);
    }

    public List<String> preservedEmailsLower() {
        return preservedEmailsLower;
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
    }
}
