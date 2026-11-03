package com.jobmailer.job_mailer.service;

import com.jobmailer.job_mailer.model.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/*
*
* @ConditionalOnProperty(...): Spring creates this bean only when jobmailer.dry-run=true.
* There is the real SMTP sender which will be the opposite: active only when dry-run=false.
* So exactly one EmailSender exists at any time, and nothing can accidentally send real emails while the flag is true.
matchIfMissing = true: if someone forgets the property entirely, we still default to dry run, which is the safe choice.
* */
@Service
@ConditionalOnProperty(name = "jobmailer.dry-run", havingValue = "true", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);


    @Override
    public void send(EmailMessage message) {
        log.info("[DRY RUN] To: {} | Subject: {}\n{}",message.to(), message.subject(), message.body());
    }
}
