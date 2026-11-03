package com.jobmailer.job_mailer.service;


import com.jobmailer.job_mailer.config.MailerProperties;
import com.jobmailer.job_mailer.exception.EmailDeliveryException;
import com.jobmailer.job_mailer.model.Company;
import com.jobmailer.job_mailer.model.EmailMessage;
import com.jobmailer.job_mailer.history.SentLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Prepares and runs one sending campaign over the approved company CSV.
 *
 * <p>The work is split in two so a person can see what is about to happen:
 * {@link #plan()} decides which emails would be sent, and {@link #execute(List)}
 * sends them. In dry-run mode nothing is delivered, nothing is written to the sent
 * log and there is no pause between messages.
 */
@Component
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    /** The campaign stops after this many failures in a row, for example a wrong password. */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final MailerProperties props;
    private final CompanyCsvReader reader;
    private final CsvApprovalService approvals;
    private final EmailTemplateRenderer renderer;
    private final EmailSender sender;
    private final SentLogRepository sentLog;

    /**
     * Creates the campaign service.
     *
     * @param props     the application settings
     * @param reader    reads the company CSV
     * @param approvals checks that a human approved the CSV
     * @param renderer  builds an email for each company
     * @param sender    delivers an email (real or dry-run, depending on the settings)
     * @param sentLog   remembers what was already sent
     */
    public CampaignService(MailerProperties props, CompanyCsvReader reader,
                           CsvApprovalService approvals, EmailTemplateRenderer renderer,
                           EmailSender sender, SentLogRepository sentLog) {
        this.props = props;
        this.reader = reader;
        this.approvals = approvals;
        this.renderer = renderer;
        this.sender = sender;
        this.sentLog = sentLog;
    }

    /**
     * Decides which emails the campaign would send now.
     *
     * <p>The CSV must be approved. Every email is rendered up front, so a problem in the
     * template (an unknown blank or unfinished {@code [brackets]}) stops the campaign
     * before anything is sent. Addresses already in the sent log are left out, and the
     * result is cut to what remains of today's daily limit.
     *
     * @return the emails to send, in CSV order; may be empty
     * @throws com.jobmailer.job_mailer.exception.ApprovalRequiredException if the CSV is not approved
     * @throws com.jobmailer.job_mailer.exception.EmailTemplateException    if the template cannot be filled in
     */
    public List<EmailMessage> plan() {
        Path csv = props.companiesPath();
        approvals.requireApproval(csv);

        List<Company> companies = reader.read(csv);
        List<EmailMessage> all = companies.stream().map(renderer::render).toList();

        Set<String> alreadySent = sentLog.sentEmails();
        List<EmailMessage> pending = all.stream()
                .filter(message -> !alreadySent.contains(message.to()))
                .toList();

        long sentToday = sentLog.countSentOn(LocalDate.now());
        long room = Math.max(0, props.dailyLimit() - sentToday);
        List<EmailMessage> plan = pending.stream().limit(room).toList();

        log.info("{} companies in file, {} already sent, {} pending; {} sent today of a daily limit of {}; {} will be sent now",
                all.size(), all.size() - pending.size(), pending.size(),
                sentToday, props.dailyLimit(), plan.size());
        return plan;
    }

    /**
     * Sends the planned emails one by one.
     *
     * <p>After each real send the address is written to the sent log and the service
     * pauses for the configured delay. A failed send is logged and the campaign carries
     * on with the next company, unless {@value #MAX_CONSECUTIVE_FAILURES} sends in a row
     * fail, in which case the campaign stops.
     *
     * @param plan the emails to send, as returned by {@link #plan()}
     * @return how many emails were sent, how many failed, and whether the campaign stopped early
     * @throws IllegalStateException if the sent log cannot be written, which stops the campaign
     */
    public CampaignResult execute(List<EmailMessage> plan) {
        int sent = 0;
        int failed = 0;
        int failuresInARow = 0;
        boolean aborted = false;

        for (int i = 0; i < plan.size(); i++) {
            EmailMessage message = plan.get(i);
            try {
                sender.send(message);
                sent++;
                failuresInARow = 0;
                if (!props.dryRun()) {
                    sentLog.recordSent(message.to(), message.subject());
                }
            } catch (EmailDeliveryException e) {
                failed++;
                failuresInARow++;
                String reason = reasonOf(e);
                log.error("Could not send to {}: {}", message.to(), reason);
                if (!props.dryRun()) {
                    sentLog.recordFailed(message.to(), reason);
                }
                if (failuresInARow >= MAX_CONSECUTIVE_FAILURES) {
                    log.error("Stopping: {} failures in a row. Check the SMTP settings and the App Password.",
                            MAX_CONSECUTIVE_FAILURES);
                    aborted = true;
                    break;
                }
            }
            boolean moreToSend = i < plan.size() - 1;
            if (!props.dryRun() && moreToSend && !pause()) {
                aborted = true;
                break;
            }
        }
        return new CampaignResult(sent, failed, aborted);
    }

    /**
     * Waits for the configured delay between two emails.
     *
     * @return {@code true} if the wait finished; {@code false} if the thread was interrupted
     */
    private boolean pause() {
        try {
            Thread.sleep(Duration.ofSeconds(props.delaySeconds()));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting; stopping the campaign");
            return false;
        }
    }

    /**
     * Extracts the most useful error text from a delivery failure.
     *
     * @param e the delivery failure
     * @return the cause's message if there is one, otherwise the failure's own message
     */
    private String reasonOf(EmailDeliveryException e) {
        String text = e.getCause() != null && e.getCause().getMessage() != null
                ? e.getCause().getMessage()
                : e.getMessage();
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * The outcome of one campaign run.
     *
     * @param sent    emails sent (or, in dry-run mode, previewed) successfully
     * @param failed  emails that could not be sent
     * @param aborted {@code true} if the campaign stopped before reaching the end of the plan
     */
    public record CampaignResult(int sent, int failed, boolean aborted) {
    }
}
