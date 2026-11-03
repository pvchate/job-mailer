package com.jobmailer.job_mailer.runner;

import com.jobmailer.job_mailer.config.MailerProperties;
import com.jobmailer.job_mailer.model.EmailMessage;
import com.jobmailer.job_mailer.service.CampaignService;
import com.jobmailer.job_mailer.service.CampaignService.CampaignResult;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Command that sends the application emails to the approved companies.
 *
 * <p>Runs only when the application is started with {@code --jobmailer.mode=send}.
 * Whether emails are really delivered depends on {@code jobmailer.dry-run}. For a real
 * send the person must type {@code SEND} after seeing how many emails will go out.
 */
@Component
@ConditionalOnProperty(name = "jobmailer.mode", havingValue = "send")
public class SendRunner implements ApplicationRunner {

    /** The exact word the person has to type to start a real send. */
    private static final String CONFIRMATION_WORD = "SEND";

    private final MailerProperties props;
    private final CampaignService campaign;
    private final ConsolePrompt prompt;

    /**
     * Creates the runner.
     *
     * @param props    the application settings
     * @param campaign prepares and runs the campaign
     * @param prompt   asks the person for the final confirmation
     */
    public SendRunner(MailerProperties props, CampaignService campaign, ConsolePrompt prompt) {
        this.props = props;
        this.campaign = campaign;
        this.prompt = prompt;
    }

    /**
     * Plans the campaign, asks for confirmation if emails will really be delivered,
     * runs it, and prints a summary.
     *
     * @param args the application arguments, not used
     */
    @Override
    public void run(ApplicationArguments args) {
        List<EmailMessage> plan = campaign.plan();
        if (plan.isEmpty()) {
            System.out.println("Nothing to send: every approved company was already sent to, "
                    + "or today's daily limit is used up.");
            return;
        }

        if (props.dryRun()) {
            System.out.println("DRY RUN: " + plan.size() + " email(s) will be shown but not sent.");
        } else {
            boolean confirmed = prompt.confirm(
                    "About to SEND " + plan.size() + " real email(s) from " + props.senderEmail()
                            + ". Type " + CONFIRMATION_WORD + " to continue.",
                    CONFIRMATION_WORD);
            if (!confirmed) {
                System.out.println("Cancelled. Nothing was sent.");
                return;
            }
        }

        CampaignResult result = campaign.execute(plan);
        System.out.println();
        System.out.println((props.dryRun() ? "Previewed: " : "Sent: ") + result.sent()
                + ", failed: " + result.failed()
                + (result.aborted() ? " (campaign stopped early, see the log)" : ""));
        if (!props.dryRun()) {
            System.out.println("Check your inbox for bounce messages (Delivery Status Notification) before the next batch.");
        }
    }
}
