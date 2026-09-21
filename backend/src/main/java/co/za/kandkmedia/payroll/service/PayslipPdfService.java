package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Generates payslips as a genuine duplicate of the company's real Sage VIP
 * payslip template (see PayslipDocxTemplateService), converted to PDF via
 * headless LibreOffice (see DocxToPdfConverter) so what employees receive
 * is a faithful render of the actual document the office uses — not a
 * separate PDFBox recreation that only approximates it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayslipPdfService {

    private final PayslipDocxTemplateService docxTemplateService;
    private final DocxToPdfConverter docxToPdfConverter;
    private final PayrollRepository payrollRepository;

    public byte[] generate(Payroll payroll) {
        BigDecimal[] yearToDateTotals = yearToDateTotals(payroll.getEmployee().getId(), payroll.getPayPeriod());
        byte[] docx = docxTemplateService.build(payroll, yearToDateTotals);
        return docxToPdfConverter.convert(docx);
    }

    private BigDecimal[] yearToDateTotals(Long employeeId, String currentPeriod) {
        try {
            YearMonth current = YearMonth.parse(currentPeriod);
            YearMonth taxYearStart = current.getMonthValue() >= 3
                    ? YearMonth.of(current.getYear(), 3)
                    : YearMonth.of(current.getYear() - 1, 3);

            BigDecimal gross = BigDecimal.ZERO, deductions = BigDecimal.ZERO, net = BigDecimal.ZERO;
            for (Payroll p : payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(employeeId)) {
                YearMonth period = YearMonth.parse(p.getPayPeriod());
                if (!period.isBefore(taxYearStart) && !period.isAfter(current)) {
                    gross = gross.add(nz(p.getGrossPay()));
                    deductions = deductions.add(nz(p.getTotalDeductions()));
                    net = net.add(nz(p.getNetPay()));
                }
            }
            return new BigDecimal[]{gross, deductions, net};
        } catch (Exception e) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        }
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
