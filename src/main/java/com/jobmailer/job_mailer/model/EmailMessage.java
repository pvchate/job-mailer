package com.jobmailer.job_mailer.model;

public record EmailMessage(String to, String subject, String body) {
}
