package com.fronzec.plugins.ticketpdf.batch;

import com.fronzec.plugins.ticketpdf.domain.HmacTokenService;
import com.fronzec.plugins.ticketpdf.domain.Ticket;
import com.fronzec.plugins.ticketpdf.domain.TicketDocument;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Pure CPU processor: signs the ticket with HMAC-SHA256, generates a QR code PNG,
 * and renders a PDF document with OpenPDF.
 *
 * <p>No I/O or DB access — all side effects live in {@link TicketFileItemWriter}.
 * On any error, the exception propagates (fail-fast, no skip in v1).
 */
public class TicketDocumentProcessor implements ItemProcessor<Ticket, TicketDocument> {

    private static final Logger log = LoggerFactory.getLogger(TicketDocumentProcessor.class);
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int QR_SIZE = 200;

    private final JobParamsHolder holder;

    public TicketDocumentProcessor(JobParamsHolder holder) {
        this.holder = holder;
    }

    @Override
    public TicketDocument process(Ticket ticket) throws Exception {
        log.debug("Processing ticket id={} code={}", ticket.getId(), ticket.getTicketCode());

        // 1. HMAC token
        String token = HmacTokenService.sign(
                ticket.getId(), ticket.getTicketCode(), holder.getTokenSecret());

        // 2. QR code as PNG bytes
        byte[] qrPng = generateQrPng(token);

        // 3. PDF document
        byte[] pdfBytes = generatePdf(ticket, qrPng);

        return new TicketDocument(ticket.getId(), ticket.getTicketCode(), pdfBytes);
    }

    private byte[] generateQrPng(String content) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] generatePdf(Ticket ticket, byte[] qrPng) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

        doc.add(new Paragraph("Event Ticket", titleFont));
        doc.add(new Paragraph(" "));

        addField(doc, "Event:", ticket.getEventName(), labelFont, valueFont);
        addField(doc, "Holder:", ticket.getHolderName(), labelFont, valueFont);
        addField(doc, "Seat:", ticket.getSeat() != null ? ticket.getSeat() : "—", labelFont, valueFont);
        addField(doc, "Location:", ticket.getEventLocation() != null ? ticket.getEventLocation() : "—", labelFont, valueFont);
        addField(doc, "Date/Time:", ticket.getEventDatetime().format(DT_FMT), labelFont, valueFont);
        addField(doc, "Ticket Code:", ticket.getTicketCode(), labelFont, valueFont);

        doc.add(new Paragraph(" "));

        // Embed QR image
        Image qrImage = Image.getInstance(qrPng);
        qrImage.scaleToFit(150, 150);
        doc.add(qrImage);

        doc.close();
        return baos.toByteArray();
    }

    private void addField(Document doc, String label, String value, Font lf, Font vf)
            throws Exception {
        Paragraph p = new Paragraph();
        p.add(new com.lowagie.text.Chunk(label + " ", lf));
        p.add(new com.lowagie.text.Chunk(value, vf));
        doc.add(p);
    }
}
