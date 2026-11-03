package com.jobmailer.job_mailer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HrNameResolver}.
 *
 * <p>The tests are grouped into addresses that must produce a first name and addresses
 * that must fall back to {@link HrNameResolver#FALLBACK_NAME}.
 */
class HrNameResolverTest {

    private final HrNameResolver resolver = new HrNameResolver();

    /**
     * Verifies that a first name is extracted from addresses of the form
     * {@code firstname<separator>lastname}, regardless of letter case or separator,
     * and that a {@code +tag} suffix is ignored.
     *
     * @param email    the address to resolve
     * @param expected the first name expected in the result
     */
    @ParameterizedTest
    @CsvSource({
            "priya.sharma@acme.com,           Priya",
            "Priya_Sharma@Acme.com,           Priya",
            "priya-sharma@acme.com,           Priya",
            "priya.sharma+jobs@acme.com,      Priya",
            "rahul.kumar.verma@acme.com,      Rahul"
    })
    void extractsFirstNameFromPersonalAddress(String email, String expected) {
        assertThat(resolver.resolve(email)).isEqualTo(expected);
    }

    /**
     * Verifies that role-based, ambiguous, malformed, empty and {@code null} addresses
     * all produce the neutral fallback instead of a guessed name.
     *
     * @param email the address to resolve
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "hr@acme.com",
            "careers@acme.com",
            "info@shivamtech.com",
            "careers-ind@innovect.com",
            "pune_careers@epam.com",
            "rahul.hr@acme.com",
            "rkulkarni@acme.com",
            "priyasharma@acme.com",
            "a.sharma@acme.com",
            "jd123.sharma@acme.com",
            "not-an-email",
            "@acme.com"
    })
    void fallsBackWhenNoReliableNameIsPresent(String email) {
        assertThat(resolver.resolve(email)).isEqualTo(HrNameResolver.FALLBACK_NAME);
    }

    /** Verifies the exact fallback text that will appear in the greeting. */
    @Test
    void fallbackNameIsSirMaam() {
        assertThat(HrNameResolver.FALLBACK_NAME).isEqualTo("Sir/Ma'am");
    }
}
