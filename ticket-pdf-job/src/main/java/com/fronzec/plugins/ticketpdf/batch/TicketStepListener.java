package com.fronzec.plugins.ticketpdf.batch;

import com.fronzec.plugins.ticketpdf.domain.Ticket;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;

/**
 * Populates {@link JobParamsHolder} from {@link StepExecution#getJobParameters()} before the
 * reader opens. This is the mechanism for injecting job parameters without {@code @StepScope}.
 *
 * <p>The listener is registered on the step; Spring Batch guarantees {@code beforeStep} is
 * called before {@link JdbcCursorItemReader#open(org.springframework.batch.item.ExecutionContext)}.
 */
public class TicketStepListener implements StepExecutionListener {

    private final JobParamsHolder holder;
    private final JdbcCursorItemReader<Ticket> reader;

    public TicketStepListener(JobParamsHolder holder, JdbcCursorItemReader<Ticket> reader) {
        this.holder = holder;
        this.reader = reader;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        var params = stepExecution.getJobParameters();

        String outputDir = params.getString("OUTPUT_DIR");
        String tokenSecret = params.getString("TOKEN_SECRET");
        String eventId = params.getString("EVENT_ID");
        String date = params.getString("DATE");

        holder.setOutputDir(outputDir);
        holder.setTokenSecret(tokenSecret);
        holder.setEventId(eventId);
        holder.setDate(date);

        // Configure the reader SQL based on whether EVENT_ID is present
        if (holder.hasEventId()) {
            reader.setSql(
                    "SELECT id, event_id, ticket_code, holder_name, event_name, event_location,"
                            + " seat, event_datetime, processed"
                            + " FROM event_tickets"
                            + " WHERE processed = FALSE AND event_id = "
                            + Long.parseLong(eventId.trim())
                            + " ORDER BY id ASC");
        } else {
            reader.setSql(
                    "SELECT id, event_id, ticket_code, holder_name, event_name, event_location,"
                            + " seat, event_datetime, processed"
                            + " FROM event_tickets"
                            + " WHERE processed = FALSE"
                            + " ORDER BY id ASC");
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }
}
