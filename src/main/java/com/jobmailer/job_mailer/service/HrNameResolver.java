package com.jobmailer.job_mailer.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Derives the value for a company's {@code hrName} from its email address.
 *
 * <p>When the address clearly looks like {@code firstname.lastname@company.com}, the
 * capitalised first name is returned (for example {@code "Priya"}). In every other case
 * the neutral fallback {@value #FALLBACK_NAME} is returned, so the email template can
 * always greet the recipient with {@code "Dear <name>,"}.
 *
 * <p>The resolver is deliberately conservative: a wrong personal name in a cold email
 * is worse than a neutral greeting, so anything ambiguous falls back.
 */
@Component
public class HrNameResolver {

    /** Neutral name used whenever no reliable first name can be read from the address. */
    public static final String FALLBACK_NAME = "Sir/Ma'am";

    /**
     * Words that mark a role, department or place rather than a person, such as
     * {@code hr} or {@code careers}. An address containing any of them is never
     * treated as a personal address.
     */
    private static final Set<String> GENERIC_TOKENS = Set.of(
            "hr", "hrd", "careers", "career", "jobs", "job", "info", "contact", "admin",
            "support", "hello", "hi", "recruitment", "recruiter", "recruiters", "talent",
            "hiring", "resume", "resumes", "mail", "office", "sales", "team", "apply",
            "applications", "people", "staffing", "pune", "india", "ind");

    /** A plausible first name: lower-case letters only, at least three of them. */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z]{3,}$");

    /** Characters that separate the parts of an email's local part, such as the dot in priya.sharma. */
    private static final String SEPARATORS = "[._\\-]";

    /**
     * Returns the name to use for the {@code hrName} column of the given email address.
     *
     * @param email the recipient email address, may be {@code null}
     * @return the capitalised first name if one can be read reliably from the address,
     *         otherwise {@value #FALLBACK_NAME}
     */
    public String resolve(String email) {
        return guessFirstName(email).orElse(FALLBACK_NAME);
    }

    /**
     * Tries to read a first name from the local part of an email address.
     *
     * <p>A name is returned only when the local part has at least two parts joined by a
     * dot, underscore or hyphen, none of the parts is a generic word, and the first part
     * looks like a name. For example {@code priya.sharma} gives {@code Priya}, while
     * {@code hr}, {@code careers-ind}, {@code pune_careers} and {@code rkulkarni} give nothing.
     * A {@code +tag} suffix on the local part is ignored.
     *
     * @param email the recipient email address, may be {@code null}
     * @return the capitalised first name, or empty if the address does not clearly contain one
     */
    private Optional<String> guessFirstName(String email) {
        if (email == null) {
            return Optional.empty();
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return Optional.empty();
        }

        String local = email.substring(0, at).toLowerCase(Locale.ROOT);
        int plus = local.indexOf('+');
        if (plus > 0) {
            local = local.substring(0, plus);
        }

        String[] parts = local.split(SEPARATORS);
        if (parts.length < 2) {
            return Optional.empty();
        }
        for (String part : parts) {
            if (GENERIC_TOKENS.contains(part)) {
                return Optional.empty();
            }
        }
        if (!NAME_PATTERN.matcher(parts[0]).matches()) {
            return Optional.empty();
        }
        return Optional.of(capitalise(parts[0]));
    }

    /**
     * Upper-cases the first letter of a word.
     *
     * @param word a non-empty lower-case word
     * @return the word with its first letter in upper case
     */
    private String capitalise(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}