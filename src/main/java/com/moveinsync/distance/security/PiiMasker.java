package com.moveinsync.distance.security;

import java.util.regex.Pattern;

/**
 * Utility for masking personally identifiable information (PII) before logging or returning error responses.
 * Guarantees phone numbers, email addresses, and private resident addresses are never leaked.
 */
public final class PiiMasker {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(\\+?[0-9]{1,3}[-.\\s]?)?\\(?([0-9]{3})\\)?[-.\\s]?([0-9]{3})[-.\\s]?([0-9]{4})\\b");

    private PiiMasker() {}

    public static String mask(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        // Mask emails: j***e@domain.com
        String masked = EMAIL_PATTERN.matcher(input).replaceAll(match -> {
            String email = match.group();
            int atIndex = email.indexOf('@');
            if (atIndex <= 1) return "***" + email.substring(atIndex);
            return email.charAt(0) + "***" + email.charAt(atIndex - 1) + email.substring(atIndex);
        });

        // Mask phone numbers: ***-***-1234
        masked = PHONE_PATTERN.matcher(masked).replaceAll("***-***-$3");

        return masked;
    }

    public static String maskEmployeeId(String employeeId) {
        if (employeeId == null || employeeId.length() <= 3) return "***";
        return employeeId.substring(0, 2) + "***" + employeeId.charAt(employeeId.length() - 1);
    }
}
