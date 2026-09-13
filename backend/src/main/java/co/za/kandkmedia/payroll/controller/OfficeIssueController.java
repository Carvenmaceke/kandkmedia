package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.OfficeIssue;
import co.za.kandkmedia.payroll.dto.OfficeIssueRequestDto;
import co.za.kandkmedia.payroll.service.OfficeIssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately public (no JWT required) — same reasoning as
 * SupportController: the frontend doesn't have a real authenticated
 * session to attach yet, so submitter identity rides along in the body.
 */
@RestController
@RequestMapping("/api/public/office-issues")
@RequiredArgsConstructor
public class OfficeIssueController {

    private final OfficeIssueService officeIssueService;

    @PostMapping
    public OfficeIssue submit(@Valid @RequestBody OfficeIssueRequestDto dto) {
        return officeIssueService.submit(dto);
    }
}
