package com.jobmailer.job_mailer.history;

import com.jobmailer.job_mailer.config.MailerProperties;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps a permanent record of which emails were sent, in a CSV file.
 *
 * <p>Each line holds a timestamp, the recipient address, a status ({@code SENT} or
 * {@code FAILED}) and a detail text. Only {@code SENT} lines count as done, so a failed
 * address is tried again on the next run. The log is what prevents duplicate
 * applications and what makes the daily limit hold across several runs.
 */
@Component
public class SentLogRepository {

    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";
    private static final String[] HEADER = {"timestamp", "email", "status", "detail"};

    private final Path file;

    /**
     * Creates the repository for the sent-log file named in the settings.
     *
     * @param props the application settings
     */
    public SentLogRepository(MailerProperties props) {
        this.file = props.sentLogPath();
    }

    /**
     * Returns the addresses that were already sent successfully.
     *
     * @return the lower-case addresses with status {@code SENT}; empty if there is no log yet
     * @throws IllegalStateException if the log exists but cannot be read
     */
    public Set<String> sentEmails() {
        Set<String> emails = new HashSet<>();
        for (String[] row : readRows()) {
            if (isSent(row)) {
                emails.add(row[1].trim().toLowerCase(Locale.ROOT));
            }
        }
        return emails;
    }

    /**
     * Counts the emails sent successfully on a given calendar day, in the computer's time zone.
     *
     * @param day the day to count
     * @return the number of {@code SENT} lines recorded on that day
     * @throws IllegalStateException if the log exists but cannot be read
     */
    public long countSentOn(LocalDate day) {
        long count = 0;
        for (String[] row : readRows()) {
            if (isSent(row) && dayOf(row[0]).filter(day::equals).isPresent()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Records that an email was sent successfully.
     *
     * <p>If this fails, the exception stops the campaign on purpose: continuing without
     * a record could lead to a duplicate email on the next run.
     *
     * @param email   the recipient address
     * @param subject the subject that was sent
     * @throws IllegalStateException if the log cannot be written
     */
    public void recordSent(String email, String subject) {
        append(new String[]{Instant.now().toString(), email, STATUS_SENT, subject});
    }

    /**
     * Records that sending to an address failed.
     *
     * @param email  the recipient address
     * @param reason a short description of the error
     * @throws IllegalStateException if the log cannot be written
     */
    public void recordFailed(String email, String reason) {
        append(new String[]{Instant.now().toString(), email, STATUS_FAILED, reason});
    }

    /**
     * Tells whether a log row describes a successful send.
     *
     * @param row one row of the log
     * @return {@code true} if the row has a status column equal to {@code SENT}
     */
    private boolean isSent(String[] row) {
        return row.length >= 3 && STATUS_SENT.equals(row[2]);
    }

    /**
     * Converts a stored timestamp to a calendar day in the computer's time zone.
     *
     * @param timestamp the stored ISO-8601 timestamp
     * @return the day, or empty if the text is not a valid timestamp
     */
    private Optional<LocalDate> dayOf(String timestamp) {
        try {
            return Optional.of(Instant.parse(timestamp).atZone(ZoneId.systemDefault()).toLocalDate());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads all data rows of the log, without the header.
     *
     * @return the rows; empty if the log file does not exist
     * @throws IllegalStateException if the file cannot be read
     */
    private List<String[]> readRows() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(in)) {
            return reader.readAll().stream()
                    .filter(row -> row.length > 0 && !"timestamp".equals(row[0]))
                    .toList();
        } catch (IOException | CsvException e) {
            throw new IllegalStateException("Could not read the sent log: " + file, e);
        }
    }

    /**
     * Adds one row to the end of the log, creating the file and its header if needed.
     *
     * @param row the row to add
     * @throws IllegalStateException if the file cannot be written
     */
    private void append(String[] row) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            boolean newFile = !Files.exists(file) || Files.size(file) == 0;
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                 CSVWriter writer = new CSVWriter(out)) {
                if (newFile) {
                    writer.writeNext(HEADER, false);
                }
                writer.writeNext(row, false);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not write to the sent log: " + file, e);
        }
    }
}
