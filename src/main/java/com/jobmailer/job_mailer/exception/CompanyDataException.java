package com.jobmailer.job_mailer.exception;


/**
 * Thrown when the company input file cannot be read or is structurally invalid,
 * for example when it is missing or the required columns are absent.
 */
public class CompanyDataException extends RuntimeException {

    public CompanyDataException(String message) {
        super(message);
    }

    public CompanyDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
