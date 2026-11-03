package com.jobmailer.job_mailer.exception;

/**
 * Thrown when the email template is missing, badly formatted, or cannot be filled in,
 * for example because it contains an unknown placeholder or unfinished text.
 */
public class EmailTemplateException extends RuntimeException {

    /**
     * Creates the exception with a message that explains what is wrong with the template.
     *
     * @param message the explanation shown to the user
     */
    public EmailTemplateException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and the underlying cause.
     *
     * @param message the explanation shown to the user
     * @param cause   the original error, such as an I/O failure
     */
    public EmailTemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
