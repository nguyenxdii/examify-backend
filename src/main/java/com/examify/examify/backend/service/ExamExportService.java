package com.examify.examify.backend.service;

import com.examify.examify.backend.dto.ExportOptions;
import com.examify.examify.backend.model.Exam;
import com.examify.examify.backend.model.Question;
import com.examify.examify.backend.repository.ExamRepository;
import com.examify.examify.backend.repository.QuestionRepository;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.io.font.PdfEncodings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamExportService {

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private static final String FONT_PATH = "C:/Windows/Fonts/arial.ttf";

    private PdfFont getFont() throws IOException {
        try {
            // Try loading from resources first for portability
            var is = getClass().getResourceAsStream("/fonts/arial.ttf");
            if (is != null) {
                byte[] fontData = is.readAllBytes();
                return PdfFontFactory.createFont(fontData, PdfEncodings.IDENTITY_H);
            }
            throw new RuntimeException("Font not in resources");
        } catch (Exception e) {
            // Fallback to system font path
            return PdfFontFactory.createFont(FONT_PATH, PdfEncodings.IDENTITY_H);
        }
    }

    public byte[] exportExamToZip(String examId, ExportOptions options) throws IOException {
        log.info("Starting export for examId: {} with options: {}", examId, options);
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));
        List<Question> originalQuestions = questionRepository.findByExamId(examId);

        // Override metadata from options if provided
        String subject = (options.getSubject() != null && !options.getSubject().isEmpty()) ? options.getSubject() : exam.getSubject();
        int duration = (options.getDuration() > 0) ? options.getDuration() : (exam.getDuration() != null ? exam.getDuration() : 60);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            
            List<List<Question>> allVersions = new ArrayList<>();
            List<Integer> examCodes = new ArrayList<>();

            for (int i = 0; i < options.getNumVersions(); i++) {
                int examCode = 101 + i;
                examCodes.add(examCode);
                
                List<Question> versionQuestions = prepareVersion(originalQuestions, options);
                allVersions.add(versionQuestions);

                // 1. Generate Exam Paper
                if (options.getFormats().contains("pdf")) {
                    byte[] pdf = generateExamPdf(exam, versionQuestions, examCode, false, options, subject, duration);
                    addToZip(zos, "De_thi/De_thi_Ma_" + examCode + ".pdf", pdf);
                }
                if (options.getFormats().contains("docx")) {
                    byte[] docx = generateExamDocx(exam, versionQuestions, examCode, false, options, subject, duration);
                    addToZip(zos, "De_thi/De_thi_Ma_" + examCode + ".docx", docx);
                }

                // 2. Generate Detailed Answers
                if (options.isShowExplanations()) {
                    if (options.getFormats().contains("pdf")) {
                        byte[] pdf = generateExamPdf(exam, versionQuestions, examCode, true, options, subject, duration);
                        addToZip(zos, "Dap_an_chi_tiet/Dap_an_chi_tiet_Ma_" + examCode + ".pdf", pdf);
                    }
                    if (options.getFormats().contains("docx")) {
                        byte[] docx = generateExamDocx(exam, versionQuestions, examCode, true, options, subject, duration);
                        addToZip(zos, "Dap_an_chi_tiet/Dap_an_chi_tiet_Ma_" + examCode + ".docx", docx);
                    }
                }
            }

            // 3. Generate Answer Matrix
            byte[] matrixPdf = generateAnswerMatrixPdf(exam, allVersions, examCodes);
            addToZip(zos, "Bang_dap_an_tong_hop/Bang_dap_an_tong_hop.pdf", matrixPdf);
        }

        return baos.toByteArray();
    }

    private static class FooterHandler implements IEventHandler {
        private final PdfFont font;
        public FooterHandler(PdfFont font) { this.font = font; }
        @Override
        public void handleEvent(com.itextpdf.kernel.events.Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            Rectangle pageSize = page.getPageSize();
            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdf);
            // Use 2-arg constructor if 3-arg is not supported in this version
            try (com.itextpdf.layout.Canvas canvas = new com.itextpdf.layout.Canvas(pdfCanvas, pageSize)) {
                // "SynDe Examify - Đề thi được tạo bởi SynDe"
                String footerText = "SynDe Examify - \u0110\u1ec1 thi \u0111\u01b0\u1ee3c t\u1ea1o b\u1ecfi SynDe";
                Paragraph footer = new Paragraph(footerText)
                        .setFont(font)
                        .setFontSize(8)
                        .setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY);
                canvas.showTextAligned(footer, pageSize.getLeft() + 40, pageSize.getBottom() + 20, TextAlignment.LEFT, VerticalAlignment.BOTTOM);
                
                // "Trang X"
                String pageText = "Trang " + pdf.getPageNumber(page);
                Paragraph pageNumber = new Paragraph(pageText)
                        .setFont(font)
                        .setFontSize(8)
                        .setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY);
                canvas.showTextAligned(pageNumber, pageSize.getRight() - 40, pageSize.getBottom() + 20, TextAlignment.RIGHT, VerticalAlignment.BOTTOM);
            }
        }
    }

    private List<Question> prepareVersion(List<Question> original, ExportOptions options) {
        List<Question> shuffled = new ArrayList<>();
        for (Question q : original) {
            Question clone = new Question();
            clone.setId(q.getId());
            clone.setContent(q.getContent());
            clone.setType(q.getType());
            clone.setExplanation(q.getExplanation());
            clone.setCorrectAnswers(q.getCorrectAnswers() != null ? new ArrayList<>(q.getCorrectAnswers()) : new ArrayList<>());
            if (q.getChoices() != null) {
                List<Question.Choice> choicesClone = new ArrayList<>();
                for (Question.Choice c : q.getChoices()) {
                    Question.Choice cc = new Question.Choice();
                    cc.setKey(c.getKey());
                    cc.setContent(c.getContent());
                    choicesClone.add(cc);
                }
                clone.setChoices(choicesClone);
            }
            shuffled.add(clone);
        }

        if (options.isShuffleQuestions()) {
            Collections.shuffle(shuffled);
        }

        if (options.isShuffleAnswers()) {
            for (Question q : shuffled) {
                if (q.getChoices() != null && q.getChoices().size() > 1) {
                    List<Question.Choice> choices = new ArrayList<>(q.getChoices());
                    Collections.shuffle(choices);
                    
                    // Re-assign keys A, B, C, D...
                    char key = 'A';
                    List<String> newCorrectAnswers = new ArrayList<>();
                    for (Question.Choice c : choices) {
                        String oldKey = c.getKey();
                        String newKeyString = String.valueOf(key);
                        
                        // If this was a correct answer, update the correct answers list
                        if (q.getCorrectAnswers() != null && q.getCorrectAnswers().contains(oldKey)) {
                            newCorrectAnswers.add(newKeyString);
                        }
                        
                        c.setKey(newKeyString);
                        key++;
                    }
                    q.setChoices(choices);
                    q.setCorrectAnswers(newCorrectAnswers);
                }
            }
        }
        return shuffled;
    }

    private void addToZip(ZipOutputStream zos, String fileName, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        zos.write(content);
        zos.closeEntry();
    }

    private byte[] generateExamPdf(Exam exam, List<Question> questions, int examCode, boolean showAnswers, ExportOptions options, String subject, int duration) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);
        
        PdfFont font = getFont();
        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterHandler(font));
        
        Document document = new Document(pdf);
        document.setFont(font);

        // Header Table - Boxed Info
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
        headerTable.setBorder(new com.itextpdf.layout.borders.SolidBorder(1));
        
        Cell leftCell;
        if (showAnswers) {
            // Detailed Answer Key Header
            leftCell = new Cell().add(new Paragraph("\u0110\u00c1P \u00c1N CHI TI\u1ebeT").setBold().setFontSize(14))
                                 .add(new Paragraph("M\u00f4n thi: " + (subject != null ? subject : "....................")))
                                 .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                                 .setPadding(10);
            
            Cell rightCell = new Cell().add(new Paragraph("M\u00c3 \u0110\u1ec0 THI").setBold().setFontSize(10).setTextAlignment(TextAlignment.CENTER))
                                      .add(new Paragraph(String.valueOf(examCode)).setBold().setFontSize(24).setTextAlignment(TextAlignment.CENTER))
                                      .setVerticalAlignment(VerticalAlignment.MIDDLE)
                                      .setBorder(new com.itextpdf.layout.borders.SolidBorder(1))
                                      .setMargin(5);
            headerTable.addCell(leftCell);
            headerTable.addCell(rightCell);
        } else {
            // Regular Exam Header
            leftCell = new Cell().add(new Paragraph("H\u1ecd v\u00e0 t\u00ean th\u00ed sinh: ....................................................."))
                                 .add(new Paragraph("M\u00e3 s\u1ed1 sinh vi\u00ean/H\u1ecdc sinh: ............................................"))
                                 .add(new Paragraph("L\u1edbp / Ph\u00f2ng thi: ............................................................"))
                                 .add(new Paragraph("M\u00f4n thi: " + (subject != null ? subject : "....................")))
                                 .add(new Paragraph("Th\u1eddi gian l\u00e0m b\u00e0i: " + duration + " ph\u00fat"))
                                 .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                                 .setPadding(10)
                                 .setFontSize(10);
            
            Cell rightCell = new Cell().add(new Paragraph("M\u00c3 \u0110\u1ec0 THI").setBold().setFontSize(10).setTextAlignment(TextAlignment.CENTER))
                                      .add(new Paragraph(String.valueOf(examCode)).setBold().setFontSize(24).setTextAlignment(TextAlignment.CENTER))
                                      .setVerticalAlignment(VerticalAlignment.MIDDLE)
                                      .setBorder(new com.itextpdf.layout.borders.SolidBorder(1))
                                      .setMargin(5);
            headerTable.addCell(leftCell);
            headerTable.addCell(rightCell);
        }
        
        document.add(headerTable);
        document.add(new Paragraph("\n"));

        // Title
        document.add(new Paragraph(exam.getTitle().toUpperCase()).setBold().setFontSize(16).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("\n"));

        // Questions
        int qNum = 1;
        for (Question q : questions) {
            Paragraph qPara = new Paragraph();
            qPara.add(new com.itextpdf.layout.element.Text("C\u00e2u " + qNum + ": ").setBold());
            qPara.add(q.getContent());
            document.add(qPara);

            if (q.getType().equals("essay")) {
                if (showAnswers) {
                    Paragraph ansPara = new Paragraph("L\u1eddi gi\u1ea3i: ").setBold();
                    String solution = (q.getSampleAnswer() != null && !q.getSampleAnswer().isEmpty()) 
                                      ? q.getSampleAnswer() 
                                      : (q.getExplanation() != null ? q.getExplanation() : "Ch\u01b0a c\u00f3 l\u1eddi gi\u1ea3i m\u1eabu.");
                    ansPara.add(solution);
                    document.add(ansPara);
                } else {
                    // Estimate blank lines based on sample answer length (minimum 8 lines)
                    int lines = 8;
                    if (q.getSampleAnswer() != null) {
                        lines = Math.max(8, q.getSampleAnswer().length() / 80 + 2);
                    } else if (q.getExplanation() != null) {
                        lines = Math.max(8, q.getExplanation().length() / 80 + 2);
                    }
                    for (int i = 0; i < lines; i++) {
                        document.add(new Paragraph("............................................................................................................................................................"));
                    }
                }
            } else if (q.getChoices() != null) {
                Table choiceTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
                for (Question.Choice c : q.getChoices()) {
                    String text = c.getKey() + ". " + c.getContent();
                    Cell cCell = new Cell().add(new Paragraph(text)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
                    
                    if (showAnswers && q.getCorrectAnswers().contains(c.getKey())) {
                        cCell.setBold().setUnderline();
                    }
                    choiceTable.addCell(cCell);
                }
                document.add(choiceTable);
            }

            if (showAnswers && options.isShowExplanations() && !q.getType().equals("essay") && q.getExplanation() != null && !q.getExplanation().isEmpty()) {
                document.add(new Paragraph("H\u01b0\u1edbng d\u1eabn: " + q.getExplanation()).setItalic().setFontSize(9).setFontColor(com.itextpdf.kernel.colors.ColorConstants.DARK_GRAY));
            }
            
            document.add(new Paragraph("\n"));
            qNum++;
        }

        document.add(new Paragraph("-------------------------- H\u1ebeT --------------------------").setTextAlignment(TextAlignment.CENTER).setBold().setMarginTop(20));

        if (options.getGreeting() != null && !options.getGreeting().isEmpty()) {
            document.add(new Paragraph("\n" + options.getGreeting()).setItalic().setTextAlignment(TextAlignment.CENTER).setFontSize(11));
        }

        document.close();
        return out.toByteArray();
    }

    private byte[] generateExamDocx(Exam exam, List<Question> questions, int examCode, boolean showAnswers, ExportOptions options, String subject, int duration) throws IOException {
        XWPFDocument doc = new XWPFDocument();

        // Header Table - Boxed Info
        XWPFTable headerTable = doc.createTable(1, 2);
        headerTable.setWidth("100%");
        
        XWPFTableCell leftCell = headerTable.getRow(0).getCell(0);
        if (showAnswers) {
            XWPFParagraph pHeader = leftCell.getParagraphs().get(0);
            XWPFRun rHeader = pHeader.createRun();
            rHeader.setBold(true);
            rHeader.setFontSize(14);
            rHeader.setText("\u0110\u00c1P \u00c1N CHI TI\u1ebeT");
            leftCell.addParagraph().createRun().setText("M\u00f4n thi: " + (subject != null ? subject : "...................."));
        } else {
            leftCell.getParagraphs().get(0).createRun().setText("H\u1ecd v\u00e0 t\u00ean th\u00ed sinh: .....................................................");
            leftCell.addParagraph().createRun().setText("M\u00e3 s\u1ed1 sinh vi\u00ean/H\u1ecdc sinh: ............................................");
            leftCell.addParagraph().createRun().setText("L\u1edbp / Ph\u00f2ng thi: ............................................................");
            leftCell.addParagraph().createRun().setText("M\u00f4n thi: " + (subject != null ? subject : "...................."));
            leftCell.addParagraph().createRun().setText("Th\u1eddi gian l\u00e0m b\u00e0i: " + duration + " ph\u00fat");
        }
        
        XWPFTableCell rightCell = headerTable.getRow(0).getCell(1);
        XWPFParagraph p3 = rightCell.getParagraphs().get(0);
        p3.setAlignment(ParagraphAlignment.CENTER);
        p3.createRun().setText("M\u00c3 \u0110\u1ec0 THI");
        XWPFParagraph p4 = rightCell.addParagraph();
        p4.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r4 = p4.createRun();
        r4.setBold(true);
        r4.setFontSize(24);
        r4.setText(String.valueOf(examCode));

        doc.createParagraph().createRun().addBreak();

        // Title
        XWPFParagraph titleP = doc.createParagraph();
        titleP.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleR = titleP.createRun();
        titleR.setBold(true);
        titleR.setFontSize(16);
        titleR.setText(exam.getTitle().toUpperCase());

        doc.createParagraph().createRun().addBreak();

        // Questions
        int qNum = 1;
        for (Question q : questions) {
            XWPFParagraph qP = doc.createParagraph();
            XWPFRun qR = qP.createRun();
            qR.setBold(true);
            qR.setText("C\u00e2u " + qNum + ": ");
            XWPFRun qContentR = qP.createRun();
            qContentR.setText(q.getContent());

            if (q.getType().equals("essay")) {
                if (showAnswers) {
                    XWPFParagraph ansP = doc.createParagraph();
                    XWPFRun ansR = ansP.createRun();
                    ansR.setBold(true);
                    ansR.setText("L\u1eddi gi\u1ea3i: ");
                    XWPFRun ansContentR = ansP.createRun();
                    String solution = (q.getSampleAnswer() != null && !q.getSampleAnswer().isEmpty()) 
                                      ? q.getSampleAnswer() 
                                      : (q.getExplanation() != null ? q.getExplanation() : "Ch\u01b0a c\u00f3 l\u1eddi gi\u1ea3i m\u1eabu.");
                    ansContentR.setText(solution);
                } else {
                    int lines = 8;
                    if (q.getSampleAnswer() != null) {
                        lines = Math.max(8, q.getSampleAnswer().length() / 80 + 2);
                    } else if (q.getExplanation() != null) {
                        lines = Math.max(8, q.getExplanation().length() / 80 + 2);
                    }
                    for (int i = 0; i < lines; i++) {
                        doc.createParagraph().createRun().setText("............................................................................................................................................................");
                    }
                }
            } else if (q.getChoices() != null) {
                for (Question.Choice c : q.getChoices()) {
                    XWPFParagraph cP = doc.createParagraph();
                    cP.setIndentationLeft(720); // 0.5 inch
                    XWPFRun cR = cP.createRun();
                    boolean isCorrect = showAnswers && q.getCorrectAnswers().contains(c.getKey());
                    if (isCorrect) {
                        cR.setBold(true);
                        cR.setUnderline(UnderlinePatterns.SINGLE);
                    }
                    cR.setText(c.getKey() + ". " + c.getContent());
                }
            }

            if (showAnswers && options.isShowExplanations() && !q.getType().equals("essay") && q.getExplanation() != null && !q.getExplanation().isEmpty()) {
                XWPFParagraph exP = doc.createParagraph();
                exP.setIndentationLeft(720);
                XWPFRun exR = exP.createRun();
                exR.setItalic(true);
                exR.setFontSize(10);
                exR.setText("H\u01b0\u1edbng d\u1eabn: " + q.getExplanation());
            }

            doc.createParagraph().createRun(); // spacing
            qNum++;
        }

        XWPFParagraph endP = doc.createParagraph();
        endP.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun endR = endP.createRun();
        endR.setBold(true);
        endR.setText("-------------------------- H\u1ebeT --------------------------");

        if (options.getGreeting() != null && !options.getGreeting().isEmpty()) {
            XWPFParagraph greetP = doc.createParagraph();
            greetP.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun greetR = greetP.createRun();
            greetR.setItalic(true);
            greetR.setText(options.getGreeting());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        doc.close();
        return out.toByteArray();
    }

    private byte[] generateAnswerMatrixPdf(Exam exam, List<List<Question>> allVersions, List<Integer> codes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);
        PdfFont font = getFont();
        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterHandler(font));
        Document document = new Document(pdf);
        document.setFont(font);

        document.add(new Paragraph("B\u1ea2NG \u0110\u00c1P \u00c1N T\u1ed4NG H\u1ee2P").setBold().setFontSize(18).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("\u0110\u1ec1 thi: " + exam.getTitle()).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("\n"));

        // Split into chunks of 4 codes
        for (int i = 0; i < codes.size(); i += 4) {
            int end = Math.min(i + 4, codes.size());
            List<Integer> subCodes = codes.subList(i, end);
            List<List<Question>> subVersions = allVersions.subList(i, end);

            float[] columnWidths = new float[subCodes.size() + 1];
            java.util.Arrays.fill(columnWidths, 1f);
            Table table = new Table(UnitValue.createPercentArray(columnWidths)).useAllAvailableWidth();
            
            // Header Row
            table.addHeaderCell(new Cell().add(new Paragraph("C\u00e2u").setBold()).setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY).setTextAlignment(TextAlignment.CENTER));
            for (Integer code : subCodes) {
                table.addHeaderCell(new Cell().add(new Paragraph("M\u00e3 " + code).setBold()).setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY).setTextAlignment(TextAlignment.CENTER));
            }

            int maxQ = subVersions.get(0).size();
            for (int qIdx = 0; qIdx < maxQ; qIdx++) {
                table.addCell(new Cell().add(new Paragraph(String.valueOf(qIdx + 1))).setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER));
                for (int vIdx = 0; vIdx < subVersions.size(); vIdx++) {
                    Question q = subVersions.get(vIdx).get(qIdx);
                    String ans;
                    if ("essay".equals(q.getType())) {
                        ans = "T\u1ef1 lu\u1eadn";
                    } else {
                        ans = (q.getCorrectAnswers() != null) ? String.join(", ", q.getCorrectAnswers()) : "";
                    }
                    table.addCell(new Cell().add(new Paragraph(ans)).setTextAlignment(TextAlignment.CENTER));
                }
            }
            document.add(table);
            document.add(new Paragraph("\n"));
        }

        document.close();
        return out.toByteArray();
    }
}
