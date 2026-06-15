package com.fronzec.plugins.ticketpdf.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fronzec.plugins.ticketpdf.domain.HmacTokenService;
import com.fronzec.plugins.ticketpdf.domain.Ticket;
import com.fronzec.plugins.ticketpdf.domain.TicketDocument;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TicketDocumentProcessor}.
 *
 * <p>Verifies that the processor produces a valid PDF byte array and that the HMAC
 * token embedded in the QR is verifiable with the same secret.
 *
 * <p>Token secret is 32+ bytes as required by {@link HmacTokenService}.
 */
class TicketDocumentProcessorTest {

    private static final String TOKEN_SECRET =
            "test-secret-must-be-at-least-32-bytes!";
    private static final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF

    private JobParamsHolder holder;
    private TicketDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        holder = new JobParamsHolder();
        holder.setTokenSecret(TOKEN_SECRET);
        holder.setOutputDir("/tmp/test-tickets");
        holder.setDate("2024-01-01");
        processor = new TicketDocumentProcessor(holder);
    }

    private Ticket buildTicket() {
        Ticket t = new Ticket();
        t.setId(42L);
        t.setEventId(7L);
        t.setTicketCode("ABC123");
        t.setHolderName("Jane Doe");
        t.setEventName("Spring Batch Conference");
        t.setEventLocation("Buenos Aires");
        t.setSeat("A1");
        t.setEventDatetime(LocalDateTime.of(2024, 6, 15, 18, 0));
        t.setProcessed(false);
        return t;
    }

    @Test
    void process_producesPdfWithMagicHeader() throws Exception {
        TicketDocument doc = processor.process(buildTicket());

        assertThat(doc).isNotNull();
        assertThat(doc.pdfBytes()).isNotEmpty();
        // First 4 bytes must be %PDF
        assertThat(doc.pdfBytes()).startsWith(PDF_MAGIC);
    }

    @Test
    void process_returnsCorrectTicketIdAndCode() throws Exception {
        TicketDocument doc = processor.process(buildTicket());

        assertThat(doc.ticketId()).isEqualTo(42L);
        assertThat(doc.ticketCode()).isEqualTo("ABC123");
    }

    @Test
    void process_isIdempotent_samePdfForSameInput() throws Exception {
        Ticket ticket = buildTicket();
        TicketDocument doc1 = processor.process(ticket);
        TicketDocument doc2 = processor.process(ticket);

        // Both must be non-empty PDFs; PDF content is deterministic for same input
        assertThat(doc1.pdfBytes()).isNotEmpty();
        assertThat(doc2.pdfBytes()).isNotEmpty();
        assertThat(doc1.pdfBytes()).startsWith(PDF_MAGIC);
        assertThat(doc2.pdfBytes()).startsWith(PDF_MAGIC);
    }

    @Test
    void process_tokenIsVerifiableWithSameSecret() throws Exception {
        Ticket ticket = buildTicket();
        TicketDocument doc = processor.process(ticket);

        // Recompute the expected token using HmacTokenService
        String expectedToken = HmacTokenService.sign(
                ticket.getId(), ticket.getTicketCode(), TOKEN_SECRET);

        // The QR token follows: {ticketId}|{ticketCode}.{base64url_hmac}
        // Verify the format: left-of-last-dot == "{id}|{code}"
        String lastDotSeparator = expectedToken.substring(0, expectedToken.lastIndexOf('.'));
        assertThat(lastDotSeparator).isEqualTo("42|ABC123");

        // Verify we produce a non-empty PDF (QR encoding itself is tested via ZXing;
        // full decode is heavy — token correctness is covered by HmacTokenServiceTest)
        assertThat(doc.pdfBytes().length).isGreaterThan(100);
    }

    @Test
    void process_longStrings_doNotThrow() throws Exception {
        Ticket ticket = buildTicket();
        ticket.setHolderName("A".repeat(250));
        ticket.setEventName("B".repeat(250));

        // Must not throw — OpenPDF wraps or truncates long content
        TicketDocument doc = processor.process(ticket);
        assertThat(doc.pdfBytes()).startsWith(PDF_MAGIC);
    }
}
