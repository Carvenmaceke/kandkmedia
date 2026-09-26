package co.za.kandkmedia.payroll.dto;

import java.util.List;

/** The chat so far, oldest first. `role` is "user" or "assistant". */
public record AssistantRequest(List<Message> messages) {
    public record Message(String role, String content) {}
}
