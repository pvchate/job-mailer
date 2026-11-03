package com.jobmailer.job_mailer.exception;


/**
 * Thrown when emails are about to be sent but the company CSV has not been approved
 * by a human, or has been changed since it was approved.
 */
public class ApprovalRequiredException extends RuntimeException {

    /**
     * Creates the exception with an explanation of what the user has to do.
     *
     * @param message the explanation shown to the user
     */
    public ApprovalRequiredException(String message) {
        super(message);
    }
}
