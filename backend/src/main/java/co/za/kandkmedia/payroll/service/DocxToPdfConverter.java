package co.za.kandkmedia.payroll.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Converts a .docx to .pdf via headless LibreOffice, so generated payslips
 * are byte-for-byte what Word/LibreOffice render from the real template
 * (see PayslipDocxTemplateService) rather than a separately hand-drawn
 * approximation.
 *
 * Each call runs in its own temp directory with its own LibreOffice user
 * profile (-env:UserInstallation) — required for correctness under
 * concurrent payslip generation, since multiple soffice invocations
 * sharing one profile directory contend for the same lock file and fail.
 */
@Service
public class DocxToPdfConverter {

    @Value("${app.soffice-path:soffice}")
    private String sofficePath = "soffice";

    private static final long TIMEOUT_SECONDS = 60;

    public byte[] convert(byte[] docxBytes) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("payslip-docx-");
        } catch (IOException e) {
            throw new RuntimeException("Could not create temp directory for payslip conversion", e);
        }
        try {
            Path inputDocx = workDir.resolve("input.docx");
            Files.write(inputDocx, docxBytes);
            Path profileDir = workDir.resolve("lo-profile");

            ProcessBuilder pb = new ProcessBuilder(
                    sofficePath,
                    "--headless",
                    "--norestore",
                    "-env:UserInstallation=file://" + profileDir,
                    "--convert-to", "pdf",
                    "--outdir", workDir.toString(),
                    inputDocx.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("LibreOffice payslip conversion timed out after " + TIMEOUT_SECONDS + "s. Output: " + output);
            }
            if (process.exitValue() != 0) {
                throw new RuntimeException("LibreOffice payslip conversion failed (exit " + process.exitValue() + "). Output: " + output);
            }

            Path outputPdf = workDir.resolve("input.pdf");
            if (!Files.exists(outputPdf)) {
                throw new RuntimeException("LibreOffice reported success but no PDF was produced. Output: " + output);
            }
            return Files.readAllBytes(outputPdf);
        } catch (IOException e) {
            throw new RuntimeException("Failed to run LibreOffice payslip conversion", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while converting payslip to PDF", e);
        } finally {
            deleteRecursively(workDir);
        }
    }

    private void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
            // Best-effort cleanup — a leftover temp dir isn't worth failing the request over.
        }
    }
}
