package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.dto.AssistantRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers IT Assistant chat messages with a Groq-hosted LLM (Groq's
 * OpenAI-compatible chat completions API). The API key only ever lives on
 * the server (GROQ_API_KEY) — the browser talks to /api/me/assistant, never
 * to Groq directly, so the key can't be lifted from the public frontend.
 *
 * When no key is configured this throws 503, and the frontend falls back to
 * its built-in keyword answers, so the chat keeps working either way.
 */
@Service
@Slf4j
public class ItAssistantService {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int MAX_HISTORY = 12;
    private static final int MAX_MESSAGE_CHARS = 2000;
    private static final int MAX_REQUESTS_PER_MINUTE = 15;
    private static final List<String> FALLBACK_MODELS = List.of("llama-3.3-70b-versatile", "openai/gpt-oss-120b", "openai/gpt-oss-20b", "llama-3.1-8b-instant");

    /** K and K Media's IT Operations Documentation, condensed — the same guidance the keyword fallback uses. */
    private static final String SYSTEM_PROMPT = """
            You are the IT Assistant for K and K Media (Pty) Ltd, a South African media company with offices in \
            Midrand and Sandton. You help employees fix everyday IT problems quickly. Be friendly, concise and \
            practical: give short numbered steps, at most about 150 words, in plain English.

            Company-specific guidance (from the internal IT Operations Documentation):
            - Printers/scanners: check the printer is on the network and powered, paper loaded/aligned. If still broken, \
            log an Office Issue (Printer/Scanner) with their office selected.
            - Outlook freezing: start in safe mode (Windows+R, outlook.exe /safe), disable add-ins under File > Options > \
            Add-ins, or File > Account Settings > Data Files > Settings > Compact Now.
            - Outlook crashing on startup: create a new Outlook profile (Control Panel > Mail > Show Profiles > Add) or \
            run Office Quick Repair (Settings > Apps > Microsoft Office > Modify > Quick Repair).
            - Outlook keeps asking for password: clear Windows Credential Manager entries; check "Always prompt for \
            logon credentials" under Account Settings > Security.
            - Email not sending/receiving: check account settings, clear the outbox, reduce attachment sizes, run \
            scanpst.exe (Inbox Repair Tool).
            - New company email in Outlook: Add Account > IMAP manually; incoming mail.kandkmedia.co.za port 993; \
            outgoing mail.kandkmedia.co.za port 465. Unknown password: log a Support ticket (Account/Access Issue).
            - Teams: connectivity (check internet, disable VPN/firewall temporarily, clear Teams cache); login (check \
            credentials, device date/time, MFA); messages not syncing (restart, clear cache, reinstall); audio/video \
            (hardware, app and OS permissions, test call); notifications (Teams and OS settings); files (permissions, \
            OneDrive/SharePoint connection).
            - 3CX phone system: portal https://kandkmedia.3cx.co.za:5001/ — new extensions must be set up by IT; log a ticket.
            - Slow or frozen computer: restart first; if it persists, log an Office Issue (Hardware/Equipment).
            - WiFi/network: toggle WiFi, confirm the correct office network; if still down, log an Office Issue (Network/WiFi).
            - Locked out / forgotten password for company systems, or VPN access problems: log a Support ticket \
            (Account/Access Issue).
            - Payslip or leave problems are not IT fixes: tell them to use Support (Payslip Issue / Leave Application Issue).

            Rules:
            - If you can't solve it in a few steps, or it needs hands-on or admin access, tell them to log a ticket: \
            "Report a System Issue" for software/account problems, "Report an Office Issue" for physical hardware, \
            network or printer problems at their office.
            - Never help bypass software licensing or activation, security controls, or company policies.
            - Never ask for or accept passwords. Don't invent company-specific details not listed above.
            - Only help with work IT topics; politely decline anything unrelated.
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<Long, Deque<Long>> recentRequests = new ConcurrentHashMap<>();

    @Value("${app.groq.api-key:}")
    private String apiKey;

    @Value("${app.groq.model:llama-3.3-70b-versatile}")
    private String model;

    public String model() {
        return model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String reply(Employee employee, AssistantRequest request) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The AI assistant isn't configured (GROQ_API_KEY).");
        }
        List<Map<String, String>> messages = buildMessages(employee, request);
        if (messages.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ask a question first.");
        }
        checkRateLimit(employee.getId());

        // The configured model first, then known-good Groq fallbacks, so a model
        // Groq has retired (or one this account can't use) doesn't break the chat.
        List<String> candidates = new ArrayList<>();
        candidates.add(model);
        for (String m : FALLBACK_MODELS) if (!candidates.contains(m)) candidates.add(m);

        String lastError = null;
        for (String candidate : candidates) {
            GroqResponse response;
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", candidate);
                body.put("messages", messages);
                body.put("temperature", 0.3);
                body.put("max_tokens", 500);
                response = send(objectMapper.writeValueAsString(body));
            } catch (Exception e) {
                log.error("Groq request failed", e);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Couldn't reach the AI service (Groq): " + e.getMessage());
            }
            if (response.status() >= 200 && response.status() < 300) {
                String content = contentOf(response.body());
                if (content == null || content.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI returned an empty answer.");
                }
                return content.trim();
            }
            lastError = groqErrorMessage(response);
            log.error("Groq API returned {} for model {}: {}", response.status(), candidate, response.body());
            if (!isModelProblem(response)) break; // bad key, quota, etc. — another model won't help
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI service (Groq) returned an error: " + lastError);
    }

    private String contentOf(String body) {
        try {
            JsonNode content = objectMapper.readTree(body).path("choices").path(0).path("message").path("content");
            return content.isMissingNode() ? null : content.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /** Groq's own error text, e.g. "Invalid API Key" — never includes the key itself. */
    private String groqErrorMessage(GroqResponse response) {
        try {
            String msg = objectMapper.readTree(response.body()).path("error").path("message").asText("");
            if (!msg.isBlank()) return msg + " (HTTP " + response.status() + ")";
        } catch (Exception ignored) { /* not JSON */ }
        return "HTTP " + response.status();
    }

    private boolean isModelProblem(GroqResponse response) {
        String body = response.body() == null ? "" : response.body().toLowerCase();
        return response.status() == 404 || body.contains("model_not_found") || body.contains("decommissioned")
                || body.contains("does not exist") || body.contains("model_permission") || body.contains("not supported");
    }

    /** System prompt + the last few turns, trimmed, with only user/assistant roles allowed through. */
    List<Map<String, String>> buildMessages(Employee employee, AssistantRequest request) {
        List<Map<String, String>> out = new ArrayList<>();
        String who = employee.getFirstName() != null ? employee.getFirstName() : "an employee";
        String office = employee.getOffice() != null ? employee.getOffice() : "unknown";
        out.add(Map.of("role", "system", "content", SYSTEM_PROMPT + "\nYou are talking to " + who + ", based at the " + office + " office."));

        List<AssistantRequest.Message> history = request == null || request.messages() == null ? List.of() : request.messages();
        List<AssistantRequest.Message> recent = history.subList(Math.max(0, history.size() - MAX_HISTORY), history.size());
        for (AssistantRequest.Message m : recent) {
            if (m == null || m.content() == null || m.content().isBlank()) continue;
            String role = "assistant".equals(m.role()) ? "assistant" : "user";
            String content = m.content().length() > MAX_MESSAGE_CHARS ? m.content().substring(0, MAX_MESSAGE_CHARS) : m.content();
            out.add(Map.of("role", role, "content", content));
        }
        // Drop leading assistant turns (e.g. the chat's canned greeting) — the conversation should open with the user.
        while (out.size() > 1 && "assistant".equals(out.get(1).get("role"))) out.remove(1);
        return out;
    }

    /** Keeps one person from burning through the Groq quota. */
    private void checkRateLimit(Long employeeId) {
        long now = System.currentTimeMillis();
        Deque<Long> times = recentRequests.computeIfAbsent(employeeId == null ? -1L : employeeId, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > 60_000) times.pollFirst();
            if (times.size() >= MAX_REQUESTS_PER_MINUTE) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "You're sending messages too quickly — wait a minute and try again.");
            }
            times.addLast(now);
        }
    }

    record GroqResponse(int status, String body) {}

    /** Overridable in tests so no real network call is made. */
    GroqResponse send(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new GroqResponse(response.statusCode(), response.body());
    }
}
