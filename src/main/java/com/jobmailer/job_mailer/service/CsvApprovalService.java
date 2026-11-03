package com.jobmailer.job_mailer.service;


import com.jobmailer.job_mailer.exception.ApprovalRequiredException;
import com.jobmailer.job_mailer.exception.CompanyDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Records and checks a human's approval of the company CSV.
 *
 * <p>An approval is stored in a file next to the CSV, named {@code <csv name>.approved}.
 * It contains the SHA-256 fingerprint of the CSV's exact content and the time of
 * approval. Because the fingerprint covers every byte of the file, any later edit makes
 * the stored approval invalid, and the CSV must be reviewed and approved again before
 * emails can be sent.
 */
@Component
public class CsvApprovalService {

    private static final Logger log = LoggerFactory.getLogger(CsvApprovalService.class);

    /**
     * Records that the current content of the CSV has been reviewed and approved.
     *
     * @param csvFile the company CSV file
     * @throws CompanyDataException if the CSV cannot be read or the approval cannot be saved
     */
    public void approve(Path csvFile) {
        String fingerprint = fingerprintOf(csvFile);
        List<String> lines = List.of(fingerprint, "approvedAt=" + Instant.now());
        try {
            Files.write(approvalFileFor(csvFile), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CompanyDataException("Could not save approval for: " + csvFile, e);
        }
        log.info("Approved {} (fingerprint {})", csvFile, fingerprint);
    }

    /**
     * Tells whether the CSV currently has a valid approval.
     *
     * @param csvFile the company CSV file
     * @return {@code true} only if an approval exists and its fingerprint matches the
     *         current content of the CSV; {@code false} if there is no approval, it
     *         cannot be read, or the CSV has changed since it was given
     */
    public boolean isApproved(Path csvFile) {
        Path approval = approvalFileFor(csvFile);
        if (!Files.isReadable(approval)) {
            return false;
        }
        try {
            List<String> lines = Files.readAllLines(approval, StandardCharsets.UTF_8);
            return !lines.isEmpty() && lines.get(0).equals(fingerprintOf(csvFile));
        } catch (IOException e) {
            log.warn("Could not read approval file {}: {}", approval, e.getMessage());
            return false;
        }
    }

    /**
     * Fails unless the CSV has a valid approval. Called before any email is sent.
     *
     * @param csvFile the company CSV file
     * @throws ApprovalRequiredException if the CSV is not approved or changed after approval
     */
    public void requireApproval(Path csvFile) {
        if (!isApproved(csvFile)) {
            throw new ApprovalRequiredException(
                    csvFile + " has not been approved, or was changed after approval. "
                            + "Run with --jobmailer.mode=enrich, review the hrName column, "
                            + "then run with --jobmailer.mode=approve before sending.");
        }
    }

    /**
     * Removes any stored approval for the CSV. Does nothing if there is none.
     *
     * @param csvFile the company CSV file
     * @throws CompanyDataException if an existing approval file cannot be deleted
     */
    public void revoke(Path csvFile) {
        try {
            if (Files.deleteIfExists(approvalFileFor(csvFile))) {
                log.info("Previous approval of {} was removed", csvFile);
            }
        } catch (IOException e) {
            throw new CompanyDataException("Could not remove approval for: " + csvFile, e);
        }
    }

    /**
     * Returns the location of the approval file belonging to a CSV file.
     *
     * @param csvFile the company CSV file
     * @return the path {@code <csv name>.approved} in the same folder
     */
    private Path approvalFileFor(Path csvFile) {
        return csvFile.resolveSibling(csvFile.getFileName() + ".approved");
    }

    /**
     * Computes the SHA-256 fingerprint of the CSV's exact content.
     *
     * @param csvFile the company CSV file
     * @return the fingerprint as a lower-case hexadecimal string
     * @throws CompanyDataException if the file cannot be read
     */
    private String fingerprintOf(Path csvFile) {
        try {
            byte[] content = Files.readAllBytes(csvFile);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (IOException e) {
            throw new CompanyDataException("Could not read company file: " + csvFile, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
