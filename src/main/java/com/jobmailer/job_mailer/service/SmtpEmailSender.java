package com.jobmailer.job_mailer.service;

import com.jobmailer.job_mailer.config.MailerProperties;
import com.jobmailer.job_mailer.exception.EmailDeliveryException;
import com.jobmailer.job_mailer.model.EmailMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Service
@ConditionalOnProperty(name = "jobmailer.dry-run", havingValue = "false")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final MailerProperties props;

    public SmtpEmailSender(JavaMailSender mailSender, MailerProperties props) {
        if (!Files.isReadable(props.resumePath())) {
            throw new IllegalStateException("Resume not found or unreadable: " + props.resumePath());
        }
        this.mailSender = mailSender;
        this.props = props;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());

            helper.setFrom(props.senderEmail(), props.senderName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.body(), false);
            helper.addAttachment(props.resumePath().getFileName().toString(),
                    new FileSystemResource(props.resumePath()));

            mailSender.send(mime);
            log.info("Email sent to {}", message.to());
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            throw new EmailDeliveryException("Failed to send email to " + message.to(), e);
        }
    }
}
