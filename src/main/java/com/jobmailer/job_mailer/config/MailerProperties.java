package com.jobmailer.job_mailer.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "jobmailer")
public record MailerProperties(
        @NotBlank String senderName,
        @NotBlank @Email String senderEmail,
        @NotBlank String senderContact,
        @NotBlank String senderLinkedIn,
        @NotNull Path resumePath,
        @NotNull Path companiesPath,
        @NotNull Path sentLogPath,
        @Min(1) @Max(200) int dailyLimit,
        @Min(10) int delaySeconds,
        boolean dryRun) {
}
