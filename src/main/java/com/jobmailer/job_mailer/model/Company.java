package com.jobmailer.job_mailer.model;

/**
 * One target company read from the input CSV.
 *
 * <p>Only {@code name} and {@code email} are required. The other fields are optional
 * and are {@code null} when the CSV cell is empty.
 */
public record Company(
        String name,
        String email,
        String hrName,
        String city,
        String role) {

    public Company {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim().toLowerCase();
        hrName = blankToNull(hrName);
        city = blankToNull(city);
        role = blankToNull(role);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank() || value.trim().equalsIgnoreCase("not available")) {
            return null;
        }
        return value.trim();
    }
}