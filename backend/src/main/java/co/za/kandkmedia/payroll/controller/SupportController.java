package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.SupportTicket;
import co.za.kandkmedia.payroll.dto.SupportRequestDto;
import co.za.kandkmedia.payroll.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately public (no JWT required) — the frontend prototype doesn't
 * have a real authenticated session to attach to this call, so submitter
 * identity rides along in the request body instead (see SupportRequestDto).
 * A real production deployment would likely want this behind auth once the
 * frontend has a real login flow talking to this backend; noted in
 * backend/README.md.
 */
@RestController
@RequestMapping("/api/public/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @PostMapping
    public SupportTicket submit(@Valid @RequestBody SupportRequestDto dto) {
        return supportService.submit(dto);
    }
}
