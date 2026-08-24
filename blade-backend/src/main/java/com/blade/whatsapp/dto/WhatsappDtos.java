package com.blade.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class WhatsappDtos {
    private WhatsappDtos() {}

    public record CollectorCreateRequest(
            @NotBlank @Size(max=100) String name,
            @NotBlank @Size(max=191) String accountRef,
            @Size(max=100) String displayName,
            @Pattern(regexp="[0-9]{6,32}") String phoneNormalized,
            @Pattern(regexp="[0-9a-fA-F]{64}") String sourceInstanceHash) {}

    public record CollectorCredential(Long accountId, Long keyId, String collectorKey, String keyPrefix,
                                      List<String> scopes) {}

    public record BatchStartRequest(
            @NotBlank @Size(max=64) @JsonAlias("batch_id") String batchNo,
            @NotNull @JsonAlias("snapshot_at") LocalDateTime snapshotAt,
            @Size(max=32) @JsonAlias("source_app_version") String sourceAppVersion,
            @Pattern(regexp="[0-9a-fA-F]{64}") @JsonAlias("chat_schema_hash") String chatSchemaHash,
            @Pattern(regexp="[0-9a-fA-F]{64}") @JsonAlias("contact_schema_hash") String contactSchemaHash,
            Map<String,Object> manifest) {}

    public record BatchResult(Long batchId, String batchNo, String status, long inserted, long updated) {}

    public record ContactItem(
            @NotBlank @JsonAlias("whatsapp_id") String contactJid,
            @JsonAlias("lid") String lidJid,
            @JsonAlias("phone_normalized") String phoneNormalized,
            @JsonAlias("display_name") String displayName,
            @JsonAlias("push_name") String pushName,
            @JsonAlias("business_name") String businessName,
            @JsonAlias("about_text") String aboutText,
            @JsonAlias("is_business") Boolean business,
            @JsonAlias("first_seen_at") LocalDateTime firstSeenAt,
            @JsonAlias("last_seen_at") LocalDateTime lastSeenAt,
            Map<String,Object> metadata) {}

    public record ConversationItem(
            @NotBlank @JsonAlias("conversation_jid") String conversationJid,
            @JsonAlias("contact_jid") String contactJid,
            @JsonAlias("source_session_pk") Long sourceSessionPk,
            String title,
            @JsonAlias("first_message_at") LocalDateTime firstMessageAt,
            @JsonAlias("last_message_at") LocalDateTime lastMessageAt,
            @JsonAlias("message_count") Long messageCount,
            @JsonAlias("unread_count") Integer unreadCount,
            Boolean archived, Boolean hidden,
            Map<String,Object> metadata) {}

    public record SourceRefItem(
            @JsonAlias("source_database") String sourceDatabase,
            @JsonAlias("source_entity") String sourceEntity,
            @NotNull @JsonAlias("source_pk") Long sourcePk,
            @JsonAlias("source_opt") Integer sourceOpt,
            @JsonAlias("source_sort") Long sourceSort,
            @NotBlank @Pattern(regexp="[0-9a-fA-F]{64}") @JsonAlias("row_hash") String rowHash) {}

    public record MessageItem(
            @NotBlank @Pattern(regexp="[0-9a-fA-F]{64}") @JsonAlias("logical_key_hash") String logicalKeyHash,
            @NotBlank @JsonAlias("conversation_jid") String conversationJid,
            @JsonAlias("contact_jid") String contactJid,
            @JsonAlias("external_message_id") String externalMessageId,
            @NotBlank String direction,
            @JsonAlias("sender_jid") String senderJid,
            @NotBlank @JsonAlias("message_type") String messageType,
            @JsonAlias("source_message_type") Integer sourceMessageType,
            @JsonAlias("mapping_version") String mappingVersion,
            @JsonAlias("text_content") String textContent,
            @NotNull @JsonAlias("source_timestamp_ms") Long sourceTimestampMs,
            @NotNull @JsonAlias("sent_at") LocalDateTime sentAt,
            String status,
            @JsonAlias("is_starred") Boolean starred,
            @JsonAlias("content_hash") @Pattern(regexp="[0-9a-fA-F]{64}") String contentHash,
            @Valid @JsonAlias("source_refs") List<SourceRefItem> sourceRefs,
            Map<String,Object> metadata) {}

    public record IssueItem(
            @NotBlank @JsonAlias("issue_key") String issueKey,
            @Pattern(regexp="[0-9a-fA-F]{64}") @JsonAlias("logical_key_hash") String logicalKeyHash,
            @JsonAlias("conversation_jid") String conversationJid,
            @NotBlank @JsonAlias("issue_type") String issueType,
            @JsonAlias("media_type") String mediaType,
            String severity,
            Map<String,Object> detail) {}

    public record ImportRequest<T>(@NotBlank String batchNo, @NotEmpty @Valid List<T> items) {}

    public record BatchCompleteRequest(
            @NotBlank String status,
            @PositiveOrZero Long sourceRowCount,
            @PositiveOrZero Long logicalMessageCount,
            @PositiveOrZero Long duplicateCount,
            @PositiveOrZero Long errorCount,
            @Size(max=1000) String errorSummary) {}

    public record ImportCounts(long inserted, long updated) {}

    public record MediaMetadata(
            @NotBlank String batchNo,
            @NotBlank @Pattern(regexp="[0-9a-fA-F]{64}") String logicalKeyHash,
            @NotBlank @Pattern(regexp="[0-9a-fA-F]{64}") String mediaKeyHash,
            @Pattern(regexp="[0-9a-fA-F]{64}") String fileHash,
            @NotBlank String mediaType,
            String mimeType,
            String originalName,
            String sourceRelativePath,
            Long durationMs, Integer width, Integer height, String caption) {}

    public record MediaItem(
            @NotBlank @Pattern(regexp="[0-9a-fA-F]{64}") @JsonAlias("logical_key_hash") String logicalKeyHash,
            @NotBlank @Pattern(regexp="[0-9a-fA-F]{64}") @JsonAlias("media_key_hash") String mediaKeyHash,
            @NotBlank @JsonAlias("media_type") String mediaType,
            @JsonAlias("mime_type") String mimeType,
            @JsonAlias("original_name") String originalName,
            @PositiveOrZero @JsonAlias("expected_file_size") Long expectedFileSize,
            @PositiveOrZero @JsonAlias("actual_file_size") Long actualFileSize,
            @Pattern(regexp="[0-9a-fA-F]{64}") @JsonAlias("file_hash") String fileHash,
            String caption,
            @PositiveOrZero @JsonAlias("duration_ms") Long durationMs,
            Integer width, Integer height,
            @JsonAlias("source_relative_path") String sourceRelativePath,
            @NotBlank @JsonAlias("download_status") String downloadStatus) {}

    public record MediaResult(Long mediaId, Long fileId, String status, boolean reused) {}

    public record IssueSummary(long open, long resolved, long missingPath, long missingFile,
                               long image, long video, long audio, LocalDateTime lastScanAt,
                               String lastScanStatus) {}

    public record AccountView(Long id, String displayName, String accountRef, String phoneNormalized,
                              LocalDateTime lastSyncTime, Integer status) {}

    public record BindingView(Long id, Long contactId, String contactName, String phoneNormalized,
                              Long customerId, String customerName, String matchMethod,
                              String status, LocalDateTime createTime) {}

    public record BindingDecision(@NotBlank @Pattern(regexp="CONFIRMED|REJECTED") String status,
                                  @Size(max=500) String note) {}

    public record IssueView(Long id, Long accountId, Long conversationId, String conversationTitle,
                            Long customerId, String customerName,
                            String conversationJid, Long messageId, LocalDateTime messageTime,
                            String direction, String issueType, String status, String severity,
                            String mediaType, int occurrenceCount, LocalDateTime firstDetectedAt,
                            LocalDateTime lastDetectedAt, LocalDateTime resolvedAt) {}

    public record ScanJobView(Long id, Long accountId, String accountName, String status,
                              LocalDateTime requestedAt, LocalDateTime claimedAt,
                              LocalDateTime completedAt, Long resultBatchId, String errorSummary) {}

    public record ScanJobCompleteRequest(@NotBlank String status, String batchNo,
                                         @Size(max=500) String errorSummary) {}
}
