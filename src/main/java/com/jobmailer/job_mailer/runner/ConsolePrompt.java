package com.jobmailer.job_mailer.runner;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Asks the person at the keyboard to confirm an action by typing an exact word.
 *
 * <p>The question is written to standard output rather than the log, because it is an
 * interactive message and not a log event.
 */
@Component
public class ConsolePrompt {

    private final BufferedReader input;

    /** Creates a prompt that reads the answer from the console (standard input). */
    public ConsolePrompt() {
        this(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
    }

    /**
     * Creates a prompt that reads the answer from the given reader. Used by tests to
     * supply an answer without a keyboard.
     *
     * @param input the source of the answer
     */
    ConsolePrompt(BufferedReader input) {
        this.input = input;
    }

    /**
     * Shows a question and waits for one line of input.
     *
     * @param question       the text shown to the person
     * @param requiredAnswer the exact word that counts as confirmation
     * @return {@code true} only if the typed line, ignoring surrounding spaces, is exactly
     *         the required answer; {@code false} for any other input or if no input is available
     * @throws UncheckedIOException if reading from the input fails
     */
    public boolean confirm(String question, String requiredAnswer) {
        System.out.println(question);
        System.out.print("> ");
        System.out.flush();
        try {
            String answer = input.readLine();
            return answer != null && answer.trim().equals(requiredAnswer);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the answer from the console", e);
        }
    }
}
