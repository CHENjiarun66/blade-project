package com.blade.whatsapp.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class WhatsappAnalysisDtos {
    private WhatsappAnalysisDtos() {}

    public record ContextMessage(Long id, LocalDateTime sentAt, String direction, String text) {}
    public record OrderFacts(long orderCount, LocalDateTime lastOrderAt, String totalAmountRange,
                             List<ProductFact> topProducts) {}
    public record ProductFact(String productName, String color, String size, long quantity) {}
    public record AnalysisJobContext(Long jobId, String contextVersion, String customerAlias,
                                     LocalDateTime windowStart, LocalDateTime windowEnd,
                                     List<ContextMessage> messages, OrderFacts orderFacts) {}

    public record AnalysisCompleteRequest(
            @NotBlank @Size(max=4000) String summary,
            Map<String,Object> preferences,
            @NotBlank @Pattern(regexp="NEW|INQUIRING|CONSIDERING|READY_TO_BUY|PURCHASED|DORMANT|UNKNOWN") String intentStage,
            @NotBlank @Pattern(regexp="POSITIVE|NEUTRAL|NEGATIVE|UNKNOWN") String sentiment,
            @NotBlank @Pattern(regexp="LOW|MEDIUM|HIGH|UNKNOWN") String churnRisk,
            LocalDateTime recommendedFollowupAt,
            @NotBlank @Size(max=1000) String recommendedAction,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            @NotEmpty @Size(max=20) List<Long> evidenceMessageIds,
            @NotBlank @Size(max=64) String provider,
            @NotBlank @Size(max=100) String model,
            @NotBlank @Size(max=32) String promptVersion) {}

    public record AnalysisFailRequest(@NotBlank @Pattern(regexp="[A-Z0-9_]{2,64}") String errorCode) {}
    public record AnalysisResult(Long analysisId, Long recommendationId, String status) {}

    public record InsightView(Long recommendationId, Long analysisId, Long customerId, String customerName,
                              String status, LocalDateTime dueAt, String summary, Map<String,Object> preferences,
                              String intentStage, String sentiment, String churnRisk, String recommendedAction,
                              BigDecimal confidence, List<Long> evidenceMessageIds, String model,
                              LocalDateTime analyzedAt, LocalDateTime handledAt, String handleNote) {}
    public record EvidenceView(Long messageId, LocalDateTime sentAt, String direction, String excerpt) {}
    public record RecommendationDecision(
            @NotBlank @Pattern(regexp="ADOPTED|DISMISSED|COMPLETED") String status,
            @Size(max=500) String note) {}
    public record WorkerCredentialRequest(@NotBlank @Size(max=100) String name) {}
    public record WorkerCredential(Long keyId, String agentKey, String keyPrefix, List<String> scopes) {}
}
