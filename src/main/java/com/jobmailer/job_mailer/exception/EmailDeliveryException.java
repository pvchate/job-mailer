package com.jobmailer.job_mailer.exception;

public class EmailDeliveryException extends RuntimeException {

     public EmailDeliveryException(String message,  Throwable cause) {
        super(message,  cause);
     }
}
