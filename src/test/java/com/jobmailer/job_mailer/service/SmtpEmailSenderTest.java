package com.jobmailer.job_mailer.service;

import com.jobmailer.job_mailer.config.MailerProperties;
import com.jobmailer.job_mailer.exception.EmailDeliveryException;
import com.jobmailer.job_mailer.model.EmailMessage;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @TempDir
    private Path tempDir;

    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() throws IOException {
        Path resume = Files.write(tempDir.resolve("resume.pdf"), new byte[]{1, 2, 3});
        sender = new SmtpEmailSender(mailSender, propsWithResume(resume));
    }

    @Test
    void sendsMessageWithCorrectRecipientAndSubject() throws Exception {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);

        sender.send(new EmailMessage("hr@acme.com", "Hello", "Body"));

        verify(mailSender).send(mime);
        assertThat(mime.getSubject()).isEqualTo("Hello");
        assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("hr@acme.com");
    }

    @Test
    void wrapsSmtpFailureInEmailDeliveryException() {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(new EmailMessage("hr@acme.com", "S", "B")))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("hr@acme.com");
    }

    @Test
    void failsFastWhenResumeIsMissing() {
        MailerProperties bad = propsWithResume(tempDir.resolve("missing.pdf"));

        assertThatThrownBy(() -> new SmtpEmailSender(mailSender, bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Resume not found");
    }

    private MailerProperties propsWithResume(Path resume) {
        return new MailerProperties(
                "Test User",
                "test@example.com",
                "9999999999",
                "https://test/linkedin.com",
                resume,
                tempDir.resolve("companies.csv"),
                tempDir.resolve("sent-log.csv"),
                25,
                45,
                false);
    }
}