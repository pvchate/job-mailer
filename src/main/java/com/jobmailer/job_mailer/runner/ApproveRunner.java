package com.jobmailer.job_mailer.runner;

import com.jobmailer.job_mailer.config.MailerProperties;
import com.jobmailer.job_mailer.model.Company;
import com.jobmailer.job_mailer.service.CompanyCsvReader;
import com.jobmailer.job_mailer.service.CsvApprovalService;
import com.jobmailer.job_mailer.service.ReviewTableFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Command that records a human's approval of the company CSV. It never sends any email.
 *
 * <p>Runs only when the application is started with {@code --jobmailer.mode=approve}.
 * It shows the review table and asks the person to type {@code APPROVE}. Approval is
 * refused when any company has no {@code hrName}, because that means the enrich step
 * was skipped.
 */
@Component
@ConditionalOnProperty(name = "jobmailer.mode", havingValue = "approve")
public class ApproveRunner implements ApplicationRunner {

    /** The exact word the person has to type to approve the file. */
    static final String CONFIRMATION_WORD = "APPROVE";

    private final MailerProperties props;
    private final CompanyCsvReader reader;
    private final CsvApprovalService approvals;
    private final ReviewTableFormatter formatter;
    private final ConsolePrompt prompt;
    private final PrintStream out;

    /**
     * Creates the runner that writes its report to the console.
     *
     * @param props     the application settings, including the CSV location
     * @param reader    reads the CSV for the review table
     * @param approvals records the approval
     * @param formatter builds the review table
     * @param prompt    asks the person for confirmation
     */
    @Autowired
    public ApproveRunner(MailerProperties props, CompanyCsvReader reader,
                         CsvApprovalService approvals, ReviewTableFormatter formatter,
                         ConsolePrompt prompt) {
        this(props, reader, approvals, formatter, prompt, System.out);
    }

    /**
     * Creates the runner with a custom output stream. Used by tests to capture the report.
     *
     * @param props     the application settings, including the CSV location
     * @param reader    reads the CSV for the review table
     * @param approvals records the approval
     * @param formatter builds the review table
     * @param prompt    asks the person for confirmation
     * @param out       where the report is written
     */
    ApproveRunner(MailerProperties props, CompanyCsvReader reader,
                  CsvApprovalService approvals, ReviewTableFormatter formatter,
                  ConsolePrompt prompt, PrintStream out) {
        this.props = props;
        this.reader = reader;
        this.approvals = approvals;
        this.formatter = formatter;
        this.prompt = prompt;
        this.out = out;
    }

    /**
     * Shows the review table and records the approval if the person confirms.
     *
     * <p>Nothing is approved when the file has no valid companies, when any company has
     * no {@code hrName}, or when the person does not type the confirmation word.
     *
     * @param args the application arguments, not used
     */
    @Override
    public void run(ApplicationArguments args) {
        Path csv = props.companiesPath();
        List<Company> companies = reader.read(csv);

        if (companies.isEmpty()) {
            out.println("Nothing to approve: no valid companies found in " + csv);
            return;
        }
        out.println();
        out.println(formatter.format(companies));
        out.println();

        if (companies.stream().anyMatch(company -> company.hrName() == null)) {
            out.println("Not approved: some rows have no hrName. Run with --jobmailer.mode=enrich first.");
            return;
        }

        boolean confirmed = prompt.confirm(
                "Have you checked every hrName above? Type " + CONFIRMATION_WORD
                        + " to approve this file for sending.",
                CONFIRMATION_WORD);
        if (confirmed) {
            approvals.approve(csv);
            out.println("Approved. You can now run with --jobmailer.mode=send.");
            out.println("If you edit the CSV again, you will need to approve it again.");
        } else {
            out.println("Not approved. Nothing was changed.");
        }
    }
}
