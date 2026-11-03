package com.jobmailer.job_mailer.runner;

import com.jobmailer.job_mailer.config.MailerProperties;
import com.jobmailer.job_mailer.model.EmailMessage;
import com.jobmailer.job_mailer.service.EmailSender;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Temporary smoke-test runner that sends a single test email to the configured
 * sender address, to verify the SMTP setup end to end (configuration, credentials
 * and resume attachment) before running a real campaign.
 *
 * <p>Active only under the {@code smoke} Spring profile, so it never runs as part of
 * a normal campaign. Start the application with
 * {@code --spring.profiles.active=smoke} to trigger it.
 *
 * <p>Whether the email is actually sent or only logged depends on
 * {@code jobmailer.dry-run}.
 */
@Component
@Profile("smoke")
public class SmokeTestRunner implements ApplicationRunner {

    private final EmailSender sender;
    private final MailerProperties props;

    public SmokeTestRunner(EmailSender sender, MailerProperties props) {
        this.sender = sender;
        this.props = props;
    }

    /** Sends one test email to the sender's own address once the application has started. */
    @Override
    public void run(ApplicationArguments args) {
        sender.send(new EmailMessage(
                props.senderEmail(),
                "job-mailer smoke test",
                "This is a test body to see if we are getting the email body."));
    }
}
