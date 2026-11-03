package com.jobmailer.job_mailer.runner;

import com.jobmailer.job_mailer.config.MailerProperties;
import com.jobmailer.job_mailer.model.Company;
import com.jobmailer.job_mailer.service.CompanyCsvEnricher;
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
 * Command that fills the {@code hrName} column of the company CSV and shows the result
 * for human review. It never sends any email.
 *
 * <p>Runs only when the application is started with {@code --jobmailer.mode=enrich}.
 * Any earlier approval of the CSV is removed first, so a file that was just enriched
 * always has to be reviewed and approved again before it can be used for sending.
 */
@Component
@ConditionalOnProperty(name = "jobmailer.mode", havingValue = "enrich")
public class EnrichRunner implements ApplicationRunner {

    private final MailerProperties props;
    private final CompanyCsvEnricher enricher;
    private final CompanyCsvReader reader;
    private final CsvApprovalService approvals;
    private final ReviewTableFormatter formatter;
    private final PrintStream out;

    /**
     * Creates the runner that writes its report to the console.
     *
     * @param props     the application settings, including the CSV location
     * @param enricher  fills the {@code hrName} column
     * @param reader    reads the enriched CSV back for the review table
     * @param approvals removes the previous approval
     * @param formatter builds the review table
     */
    @Autowired
    public EnrichRunner(MailerProperties props, CompanyCsvEnricher enricher,
                        CompanyCsvReader reader, CsvApprovalService approvals,
                        ReviewTableFormatter formatter) {
        this(props, enricher, reader, approvals, formatter, System.out);
    }

    /**
     * Creates the runner with a custom output stream. Used by tests to capture the report.
     *
     * @param props     the application settings, including the CSV location
     * @param enricher  fills the {@code hrName} column
     * @param reader    reads the enriched CSV back for the review table
     * @param approvals removes the previous approval
     * @param formatter builds the review table
     * @param out       where the report is written
     */
    EnrichRunner(MailerProperties props, CompanyCsvEnricher enricher,
                 CompanyCsvReader reader, CsvApprovalService approvals,
                 ReviewTableFormatter formatter, PrintStream out) {
        this.props = props;
        this.enricher = enricher;
        this.reader = reader;
        this.approvals = approvals;
        this.formatter = formatter;
        this.out = out;
    }

    /**
     * Revokes any old approval, fills the {@code hrName} column, and prints the review
     * table together with the next steps.
     *
     * @param args the application arguments, not used
     */
    @Override
    public void run(ApplicationArguments args) {
        Path csv = props.companiesPath();

        approvals.revoke(csv);
        int updated = enricher.fillHrNames(csv);
        List<Company> companies = reader.read(csv);

        out.println();
        out.println(formatter.format(companies));
        out.println();
        out.println(updated + " hrName value(s) were filled in " + csv + ".");
        out.println("Nothing has been sent. Next steps:");
        out.println("  1. Open the file and check the hrName column, starting with the 'check name' rows.");
        out.println("  2. Correct any wrong value by hand and save the file.");
        out.println("  3. Run again with --jobmailer.mode=approve.");
    }
}
