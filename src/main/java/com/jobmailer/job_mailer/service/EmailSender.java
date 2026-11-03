package com.jobmailer.job_mailer.service;

import com.jobmailer.job_mailer.model.EmailMessage;

public interface EmailSender {
    void send(EmailMessage message);

}
