package com.blade.whatsapp.service;

import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.file.storage.FileStorageService;
import com.blade.file.storage.StoredFile;
import com.blade.whatsapp.auth.CollectorPrincipal;
import com.blade.whatsapp.dto.WhatsappDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class WhatsappService {
    private static final Set<String> BATCH_FINAL = Set.of("SUCCEEDED", "PARTIAL", "FAILED");
    private static final Set<String> MEDIA_MIME = Set.of("image/jpeg", "image/png", "image/webp", "image/gif",
            "video/mp4", "video/webm", "video/quicktime", "audio/ogg", "audio/opus", "audio/mpeg",
            "application/pdf", "application/octet-stream");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final WhatsappAnalysisService analysisService;

    @Transactional
    public CollectorCredential createCollector(CollectorCreateRequest request, Long userId) {
        long tenantId = requiredTenant();
        byte[] sourceHash = hexOrNull(request.sourceInstanceHash());
        jdbc.update("""
                INSERT INTO wa_account(tenant_id,source_type,account_jid,phone_normalized,display_name,source_instance_hash,status,create_by)
                VALUES(?, 'MAC_APP', ?, ?, ?, ?, 1, ?)
                ON DUPLICATE KEY UPDATE phone_normalized=VALUES(phone_normalized),display_name=VALUES(display_name),
                  source_instance_hash=VALUES(source_instance_hash),status=1,deleted=0,update_time=NOW(3)
                """, tenantId, request.accountRef(), digits(request.phoneNormalized()), request.displayName(), sourceHash, userId);
        Long accountId = singleLong("SELECT id FROM wa_account WHERE tenant_id=? AND source_type='MAC_APP' AND account_jid=?",
                tenantId, request.accountRef());
        String prefix = "wac_" + randomToken(8);
        String secret = randomToken(32);
        String scopes = "batch:write,contact:write,message:write,media:write,issue:write,scan:poll";
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO wa_collector_key(tenant_id,account_id,name,key_prefix,key_hash,scopes,status,create_by)
                    VALUES(?,?,?,?,?,?,1,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId); ps.setLong(2, accountId); ps.setString(3, request.name());
            ps.setString(4, prefix); ps.setString(5, passwordEncoder.encode(secret)); ps.setString(6, scopes);
            if (userId == null) ps.setNull(7, java.sql.Types.BIGINT); else ps.setLong(7, userId);
            return ps;
        }, holder);
        return new CollectorCredential(accountId, Objects.requireNonNull(holder.getKey()).longValue(),
                prefix + "." + secret, prefix, List.of(scopes.split(",")));
    }

    @Transactional
    public BatchResult startBatch(CollectorPrincipal principal, BatchStartRequest request) {
        requireScope(principal, "batch:write");
        String scanScope = normalizeScanScope(request.scanScopeType());
        String targetPhone = "CONTACT".equals(scanScope) ? digits(request.targetPhoneNormalized()) : null;
        String targetJid = "CONTACT".equals(scanScope) ? blankToNull(request.targetConversationJid()) : null;
        if ("CONTACT".equals(scanScope) && targetPhone == null && targetJid == null)
            throw new IllegalArgumentException("客户扫描缺少目标号码或会话标识");
        int inserted = jdbc.update("""
                INSERT IGNORE INTO wa_import_batch(tenant_id,account_id,scan_scope_type,target_phone_normalized,target_conversation_jid,batch_no,snapshot_at,source_app_version,
                  chat_schema_hash,contact_schema_hash,status,manifest_json,started_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,'RUNNING',?,NOW(3))
                """, principal.tenantId(), principal.accountId(), scanScope, targetPhone, targetJid, request.batchNo(), request.snapshotAt(),
                request.sourceAppVersion(), hexOrNull(request.chatSchemaHash()), hexOrNull(request.contactSchemaHash()), json(request.manifest()));
        BatchRow row = batch(principal, request.batchNo());
        if (inserted == 0 && Set.of("FAILED", "PARTIAL").contains(row.status())) {
            jdbc.update("""
                    UPDATE wa_import_batch SET status='RUNNING',scan_scope_type=?,target_phone_normalized=?,target_conversation_jid=?,snapshot_at=?,source_app_version=?,chat_schema_hash=?,contact_schema_hash=?,
                      manifest_json=?,source_row_count=0,logical_message_count=0,inserted_count=0,updated_count=0,duplicate_count=0,
                      error_count=0,error_summary=NULL,started_at=NOW(3),completed_at=NULL
                    WHERE id=? AND tenant_id=? AND account_id=?
                    """,scanScope,targetPhone,targetJid,request.snapshotAt(),request.sourceAppVersion(),hexOrNull(request.chatSchemaHash()),hexOrNull(request.contactSchemaHash()),
                    json(request.manifest()),row.id(),principal.tenantId(),principal.accountId());
            row=new BatchRow(row.id(),"RUNNING",scanScope,targetPhone,targetJid);
        }
        return new BatchResult(row.id(), request.batchNo(), row.status(), inserted, inserted == 0 ? 1 : 0);
    }

    @Transactional
    public ImportCounts upsertContacts(CollectorPrincipal principal, ImportRequest<ContactItem> request) {
        requireScope(principal, "contact:write");
        BatchRow batch = runningBatch(principal, request.batchNo());
        Counter counter = new Counter();
        for (ContactItem item : request.items()) {
            boolean exists = exists("SELECT COUNT(*) FROM wa_contact WHERE tenant_id=? AND account_id=? AND contact_jid=?",
                    principal.tenantId(), principal.accountId(), item.contactJid());
            jdbc.update("""
                    INSERT INTO wa_contact(tenant_id,account_id,contact_jid,lid_jid,phone_normalized,display_name,push_name,
                      business_name,about_text,is_business,first_seen_at,last_seen_at,raw_metadata)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE lid_jid=VALUES(lid_jid),phone_normalized=VALUES(phone_normalized),
                      display_name=VALUES(display_name),push_name=VALUES(push_name),business_name=VALUES(business_name),
                      about_text=VALUES(about_text),is_business=VALUES(is_business),last_seen_at=VALUES(last_seen_at),
                      raw_metadata=VALUES(raw_metadata),deleted=0,update_time=NOW(3)
                    """, principal.tenantId(), principal.accountId(), item.contactJid(), item.lidJid(), digits(item.phoneNormalized()),
                    item.displayName(), item.pushName(), item.businessName(), item.aboutText(), bool(item.business()),
                    item.firstSeenAt(), item.lastSeenAt(), json(item.metadata()));
            Long contactId = contactId(principal, item.contactJid());
            createExactPhoneCandidate(principal.tenantId(), contactId, item.phoneNormalized());
            counter.add(exists);
        }
        addBatchCounts(principal.tenantId(), batch.id(), counter);
        return counter.result();
    }

    @Transactional
    public ImportCounts upsertConversations(CollectorPrincipal principal, ImportRequest<ConversationItem> request) {
        requireScope(principal, "message:write");
        BatchRow batch = runningBatch(principal, request.batchNo());
        Counter counter = new Counter();
        for (ConversationItem item : request.items()) {
            boolean exists = exists("SELECT COUNT(*) FROM wa_conversation WHERE tenant_id=? AND account_id=? AND conversation_jid=?",
                    principal.tenantId(), principal.accountId(), item.conversationJid());
            Long contactId = nullableContactId(principal, item.contactJid());
            jdbc.update("""
                    INSERT INTO wa_conversation(tenant_id,account_id,contact_id,source_session_pk,conversation_jid,
                      conversation_type,title,first_message_at,last_message_at,message_count,unread_count,archived,hidden,last_sync_time,raw_metadata)
                    VALUES(?,?,?,?,?,'DIRECT',?,?,?,?,?,?,?,NOW(3),?)
                    ON DUPLICATE KEY UPDATE contact_id=VALUES(contact_id),source_session_pk=VALUES(source_session_pk),
                      title=VALUES(title),first_message_at=COALESCE(VALUES(first_message_at),first_message_at),
                      last_message_at=VALUES(last_message_at),message_count=VALUES(message_count),unread_count=VALUES(unread_count),
                      archived=VALUES(archived),hidden=VALUES(hidden),last_sync_time=NOW(3),raw_metadata=VALUES(raw_metadata),deleted=0
                    """, principal.tenantId(), principal.accountId(), contactId, item.sourceSessionPk(), item.conversationJid(),
                    item.title(), item.firstMessageAt(), item.lastMessageAt(), zero(item.messageCount()), zero(item.unreadCount()),
                    bool(item.archived()), bool(item.hidden()), json(item.metadata()));
            counter.add(exists);
        }
        addBatchCounts(principal.tenantId(), batch.id(), counter);
        return counter.result();
    }

    @Transactional
    public ImportCounts upsertMessages(CollectorPrincipal principal, ImportRequest<MessageItem> request) {
        requireScope(principal, "message:write");
        BatchRow batch = runningBatch(principal, request.batchNo());
        Counter counter = new Counter();
        for (MessageItem item : request.items()) {
            Long conversationId = requiredConversationId(principal, item.conversationJid());
            Long contactId = nullableContactId(principal, item.contactJid());
            byte[] logicalHash = hex(item.logicalKeyHash());
            boolean exists = exists("SELECT COUNT(*) FROM wa_message WHERE tenant_id=? AND account_id=? AND logical_key_hash=?",
                    principal.tenantId(), principal.accountId(), logicalHash);
            jdbc.update("""
                    INSERT INTO wa_message(tenant_id,account_id,conversation_id,contact_id,external_message_id,logical_key_hash,
                      direction,sender_jid,message_type,source_message_type,mapping_version,text_content,source_timestamp_ms,sent_at,
                      status,is_starred,content_hash,first_seen_batch_id,last_seen_batch_id,raw_metadata)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE conversation_id=VALUES(conversation_id),contact_id=VALUES(contact_id),
                      external_message_id=VALUES(external_message_id),direction=VALUES(direction),sender_jid=VALUES(sender_jid),
                      message_type=VALUES(message_type),source_message_type=VALUES(source_message_type),mapping_version=VALUES(mapping_version),
                      text_content=VALUES(text_content),source_timestamp_ms=VALUES(source_timestamp_ms),sent_at=VALUES(sent_at),
                      status=VALUES(status),is_starred=VALUES(is_starred),content_hash=VALUES(content_hash),
                      last_seen_batch_id=VALUES(last_seen_batch_id),raw_metadata=VALUES(raw_metadata),deleted=0
                    """, principal.tenantId(), principal.accountId(), conversationId, contactId, item.externalMessageId(), logicalHash,
                    normalizedDirection(item.direction()), item.senderJid(), item.messageType(), item.sourceMessageType(),
                    item.mappingVersion() == null ? "v1" : item.mappingVersion(), item.textContent(), item.sourceTimestampMs(), item.sentAt(),
                    item.status(), bool(item.starred()), hexOrNull(item.contentHash()), batch.id(), batch.id(), json(item.metadata()));
            Long messageId = singleLong("SELECT id FROM wa_message WHERE tenant_id=? AND account_id=? AND logical_key_hash=?",
                    principal.tenantId(), principal.accountId(), logicalHash);
            for (SourceRefItem ref : Optional.ofNullable(item.sourceRefs()).orElse(List.of())) {
                jdbc.update("""
                        INSERT INTO wa_message_source_ref(tenant_id,account_id,message_id,source_database,source_entity,source_pk,
                          source_opt,source_sort,row_hash,first_seen_batch_id,last_seen_batch_id)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?)
                        ON DUPLICATE KEY UPDATE message_id=VALUES(message_id),source_opt=VALUES(source_opt),source_sort=VALUES(source_sort),
                          row_hash=VALUES(row_hash),last_seen_batch_id=VALUES(last_seen_batch_id),source_missing=0,missing_since_batch_id=NULL
                        """, principal.tenantId(), principal.accountId(), messageId,
                        ref.sourceDatabase() == null ? "chat" : ref.sourceDatabase(),
                        ref.sourceEntity() == null ? "ZWAMESSAGE" : ref.sourceEntity(), ref.sourcePk(), ref.sourceOpt(),
                        ref.sourceSort(), hex(ref.rowHash()), batch.id(), batch.id());
            }
            counter.add(exists);
        }
        addBatchCounts(principal.tenantId(), batch.id(), counter);
        return counter.result();
    }

    @Transactional
    public ImportCounts upsertIssues(CollectorPrincipal principal, ImportRequest<IssueItem> request) {
        requireScope(principal, "issue:write");
        BatchRow batch = runningBatch(principal, request.batchNo());
        Counter counter = new Counter();
        for (IssueItem item : request.items()) {
            byte[] keyHash = sha256(item.issueKey());
            boolean exists = exists("SELECT COUNT(*) FROM wa_collection_issue WHERE tenant_id=? AND account_id=? AND issue_key_hash=?",
                    principal.tenantId(), principal.accountId(), keyHash);
            Long conversationId = nullableConversationId(principal, item.conversationJid());
            Long messageId = nullableMessageId(principal, item.logicalKeyHash());
            Long mediaId = messageId == null ? null : firstMediaId(principal.tenantId(), messageId);
            jdbc.update("""
                    INSERT INTO wa_collection_issue(tenant_id,account_id,batch_id,conversation_id,message_id,media_id,issue_key_hash,
                      issue_type,status,severity,media_type,occurrence_count,first_detected_at,last_detected_at,detail_json)
                    VALUES(?,?,?,?,?,?, ?,?,'OPEN',?,?,1,NOW(3),NOW(3),?)
                    ON DUPLICATE KEY UPDATE batch_id=VALUES(batch_id),conversation_id=VALUES(conversation_id),message_id=VALUES(message_id),media_id=VALUES(media_id),
                      issue_type=VALUES(issue_type),status='OPEN',severity=VALUES(severity),media_type=VALUES(media_type),
                      occurrence_count=occurrence_count+1,last_detected_at=NOW(3),resolved_at=NULL,detail_json=VALUES(detail_json),deleted=0
                    """, principal.tenantId(), principal.accountId(), batch.id(), conversationId, messageId, mediaId, keyHash,
                    item.issueType(), item.severity() == null ? "WARNING" : item.severity(), item.mediaType(), json(item.detail()));
            counter.add(exists);
        }
        return counter.result();
    }

    @Transactional
    public ImportCounts upsertMediaMetadata(CollectorPrincipal principal, ImportRequest<MediaItem> request) {
        requireScope(principal, "media:write");
        BatchRow batch=runningBatch(principal,request.batchNo()); Counter counter=new Counter();
        for(MediaItem item:request.items()){
            Long messageId=requiredMessageId(principal,item.logicalKeyHash()); byte[] mediaKey=hex(item.mediaKeyHash());
            boolean exists=exists("SELECT COUNT(*) FROM wa_message_media WHERE tenant_id=? AND message_id=? AND media_key_hash=?",principal.tenantId(),messageId,mediaKey);
            String status=normalizeMediaStatus(item.downloadStatus());
            jdbc.update("""
                    INSERT INTO wa_message_media(tenant_id,message_id,media_key_hash,media_type,mime_type,original_name,file_size,file_hash,
                      caption,duration_ms,width,height,source_relative_path,download_status,first_seen_batch_id,last_seen_batch_id)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE media_type=VALUES(media_type),mime_type=COALESCE(VALUES(mime_type),mime_type),
                      original_name=COALESCE(VALUES(original_name),original_name),file_size=VALUES(file_size),
                      file_hash=COALESCE(VALUES(file_hash),file_hash),caption=COALESCE(VALUES(caption),caption),duration_ms=VALUES(duration_ms),
                      width=VALUES(width),height=VALUES(height),source_relative_path=VALUES(source_relative_path),
                      download_status=IF(download_status='IMPORTED','IMPORTED',VALUES(download_status)),last_seen_batch_id=VALUES(last_seen_batch_id),deleted=0
                    """,principal.tenantId(),messageId,mediaKey,item.mediaType(),item.mimeType(),safeName(item.originalName(),null),
                    item.actualFileSize()!=null?item.actualFileSize():zero(item.expectedFileSize()),hexOrNull(item.fileHash()),item.caption(),
                    item.durationMs(),item.width(),item.height(),safeRelativePath(item.sourceRelativePath()),status,batch.id(),batch.id());
            counter.add(exists);
        }
        return counter.result();
    }

    public List<String> pendingMedia(CollectorPrincipal principal, MediaPendingRequest request) {
        requireScope(principal, "media:write");
        List<String> normalized = request.mediaKeyHashes().stream().map(String::toLowerCase).distinct().toList();
        if (normalized.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(normalized.size(), "UNHEX(?)"));
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId()); args.add(principal.accountId()); args.addAll(normalized);
        Set<String> imported = new HashSet<>(jdbc.query("""
                SELECT LOWER(HEX(mm.media_key_hash))
                FROM wa_message_media mm
                JOIN wa_message m ON m.id=mm.message_id AND m.tenant_id=mm.tenant_id
                WHERE mm.tenant_id=? AND m.account_id=? AND mm.deleted=0 AND m.deleted=0
                  AND mm.download_status='IMPORTED' AND mm.media_key_hash IN (
                """+placeholders+")",(rs,row)->rs.getString(1),args.toArray()));
        return normalized.stream().filter(key -> !imported.contains(key)).toList();
    }

    @Transactional
    public BatchResult completeBatch(CollectorPrincipal principal, String batchNo, BatchCompleteRequest request) {
        requireScope(principal, "batch:write");
        if (!BATCH_FINAL.contains(request.status())) throw new IllegalArgumentException("非法批次状态");
        BatchRow batch = batch(principal, batchNo);
        if (!"RUNNING".equals(batch.status())) {
            if (batch.status().equals(request.status())) return new BatchResult(batch.id(),batchNo,batch.status(),0,1);
            throw new IllegalStateException("已结束批次不能变更状态");
        }
        jdbc.update("""
                UPDATE wa_import_batch SET status=?,source_row_count=?,logical_message_count=?,duplicate_count=?,error_count=?,
                  error_summary=?,completed_at=NOW(3) WHERE id=? AND tenant_id=? AND account_id=?
                """, request.status(), zero(request.sourceRowCount()), zero(request.logicalMessageCount()),
                zero(request.duplicateCount()), zero(request.errorCount()), request.errorSummary(), batch.id(),
                principal.tenantId(), principal.accountId());
        if (!"FAILED".equals(request.status())) {
            resolveStaleIssues(principal, batch);
            if ("ACCOUNT".equals(batch.scanScopeType())) {
                jdbc.update("UPDATE wa_account SET last_success_batch_id=?,last_sync_time=NOW(3) WHERE id=? AND tenant_id=?",
                        batch.id(), principal.accountId(), principal.tenantId());
            }
            if ("SUCCEEDED".equals(request.status())) {
                analysisService.enqueueForBatch(principal.tenantId(), batch.id());
            }
        }
        return new BatchResult(batch.id(), batchNo, request.status(), 0, 1);
    }

    private void resolveStaleIssues(CollectorPrincipal principal, BatchRow batch) {
        if ("ACCOUNT".equals(batch.scanScopeType())) {
            jdbc.update("""
                    UPDATE wa_collection_issue SET status='RESOLVED',resolved_at=NOW(3),update_time=NOW(3)
                    WHERE tenant_id=? AND account_id=? AND status='OPEN' AND batch_id<>? AND deleted=0
                    """, principal.tenantId(), principal.accountId(), batch.id());
            return;
        }
        if (batch.targetPhoneNormalized() != null) {
            jdbc.update("""
                    UPDATE wa_collection_issue i
                    JOIN wa_conversation c ON c.id=i.conversation_id AND c.tenant_id=i.tenant_id
                    JOIN wa_contact wc ON wc.id=c.contact_id AND wc.tenant_id=i.tenant_id AND wc.deleted=0
                    SET i.status='RESOLVED',i.resolved_at=NOW(3),i.update_time=NOW(3)
                    WHERE i.tenant_id=? AND i.account_id=? AND i.status='OPEN' AND i.batch_id<>?
                      AND i.deleted=0 AND wc.phone_normalized=?
                    """, principal.tenantId(), principal.accountId(), batch.id(), batch.targetPhoneNormalized());
        } else if (batch.targetConversationJid() != null) {
            jdbc.update("""
                    UPDATE wa_collection_issue i
                    JOIN wa_conversation c ON c.id=i.conversation_id AND c.tenant_id=i.tenant_id
                    SET i.status='RESOLVED',i.resolved_at=NOW(3),i.update_time=NOW(3)
                    WHERE i.tenant_id=? AND i.account_id=? AND i.status='OPEN' AND i.batch_id<>?
                      AND i.deleted=0 AND c.conversation_jid=?
                    """, principal.tenantId(), principal.accountId(), batch.id(), batch.targetConversationJid());
        }
    }

    @Transactional
    public MediaResult importMedia(CollectorPrincipal principal, MediaMetadata metadata, MultipartFile file) {
        requireScope(principal, "media:write");
        BatchRow batch = runningBatch(principal, metadata.batchNo());
        Long messageId = requiredMessageId(principal, metadata.logicalKeyHash());
        byte[] actualHash = digest(file);
        if (metadata.fileHash() != null && !MessageDigest.isEqual(actualHash, hex(metadata.fileHash()))) {
            throw new IllegalArgumentException("媒体SHA-256校验失败");
        }
        String mime = Optional.ofNullable(metadata.mimeType()).filter(v -> !v.isBlank()).orElse(file.getContentType());
        if (mime == null || !MEDIA_MIME.contains(mime.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("不支持的媒体类型");
        String hashHex = HexFormat.of().formatHex(actualHash);
        List<Long> reusable = jdbc.query("SELECT id FROM file_storage WHERE tenant_id=? AND file_hash=? AND status=1",
                (rs,row) -> rs.getLong(1), principal.tenantId(), hashHex);
        Long fileId;
        boolean reused = !reusable.isEmpty();
        if (reused) {
            fileId = reusable.get(0);
        } else {
            StoredFile stored = fileStorageService.store(file, "whatsapp");
            KeyHolder holder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO file_storage(file_key,original_name,file_name,content_type,file_size,storage_type,storage_path,
                          business_type,business_id,file_type,file_ext,file_hash,source,purpose,bind_count,visibility,status,tenant_id)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?,?, 'whatsapp','customer_chat',1,'PRIVATE',1,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                int i=1; ps.setString(i++,stored.getFileKey()); ps.setString(i++,safeName(metadata.originalName(),file.getOriginalFilename()));
                ps.setString(i++,stored.getFileName()); ps.setString(i++,mime); ps.setLong(i++,file.getSize());
                ps.setString(i++,stored.getStorageType()); ps.setString(i++,stored.getStoragePath());
                ps.setString(i++,"whatsapp_message"); ps.setLong(i++,messageId); ps.setString(i++,fileType(mime));
                ps.setString(i++,extension(stored.getFileName())); ps.setString(i++,hashHex); ps.setLong(i,principal.tenantId());
                return ps;
            }, holder);
            fileId = Objects.requireNonNull(holder.getKey()).longValue();
        }
        ensureBinding(principal.tenantId(), fileId, messageId);
        byte[] mediaKey = hex(metadata.mediaKeyHash());
        jdbc.update("""
                INSERT INTO wa_message_media(tenant_id,message_id,media_key_hash,file_id,media_type,mime_type,original_name,
                  file_size,file_hash,caption,duration_ms,width,height,source_relative_path,download_status,first_seen_batch_id,last_seen_batch_id)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?, 'IMPORTED',?,?)
                ON DUPLICATE KEY UPDATE file_id=VALUES(file_id),mime_type=VALUES(mime_type),original_name=VALUES(original_name),
                  file_size=VALUES(file_size),file_hash=VALUES(file_hash),caption=VALUES(caption),duration_ms=VALUES(duration_ms),
                  width=VALUES(width),height=VALUES(height),source_relative_path=VALUES(source_relative_path),download_status='IMPORTED',
                  error_message=NULL,last_seen_batch_id=VALUES(last_seen_batch_id),deleted=0
                """, principal.tenantId(), messageId, mediaKey, fileId, metadata.mediaType(), mime,
                safeName(metadata.originalName(), file.getOriginalFilename()), file.getSize(), actualHash, metadata.caption(),
                metadata.durationMs(), metadata.width(), metadata.height(), safeRelativePath(metadata.sourceRelativePath()), batch.id(), batch.id());
        Long mediaId = singleLong("SELECT id FROM wa_message_media WHERE tenant_id=? AND message_id=? AND media_key_hash=?",
                principal.tenantId(), messageId, mediaKey);
        jdbc.update("UPDATE wa_collection_issue SET media_id=?,status='RESOLVED',resolved_at=NOW(3) WHERE tenant_id=? AND message_id=? AND status='OPEN'",
                mediaId, principal.tenantId(), messageId);
        return new MediaResult(mediaId, fileId, "IMPORTED", reused);
    }

    public IssueSummary issueSummary() {
        long tenant = requiredTenant();
        Map<String,Object> row = jdbc.queryForMap("""
                SELECT SUM(status='OPEN') open_count,SUM(status='RESOLVED') resolved_count,
                  SUM(status='OPEN' AND issue_type IN ('MEDIA_PATH_EMPTY','LOCAL_PATH_EMPTY','THUMBNAIL_ONLY')) missing_path,
                  SUM(status='OPEN' AND issue_type NOT IN ('MEDIA_PATH_EMPTY','LOCAL_PATH_EMPTY','THUMBNAIL_ONLY')) missing_file,
                  SUM(status='OPEN' AND media_type='IMAGE') image_count,
                  SUM(status='OPEN' AND media_type='VIDEO') video_count,
                  SUM(status='OPEN' AND media_type IN ('AUDIO','VOICE')) audio_count
                FROM wa_collection_issue WHERE tenant_id=? AND deleted=0
                """, tenant);
        List<Map<String,Object>> scans = jdbc.queryForList("""
                SELECT completed_at,status FROM wa_import_batch WHERE tenant_id=? AND deleted=0
                ORDER BY started_at DESC LIMIT 1
                """, tenant);
        LocalDateTime lastAt = scans.isEmpty() ? null : asLocalDateTime(scans.get(0).get("completed_at"));
        String lastStatus = scans.isEmpty() ? null : String.valueOf(scans.get(0).get("status"));
        return new IssueSummary(number(row,"open_count"),number(row,"resolved_count"),number(row,"missing_path"),
                number(row,"missing_file"),number(row,"image_count"),number(row,"video_count"),number(row,"audio_count"),lastAt,lastStatus);
    }

    public List<AccountView> accounts() {
        long tenant=requiredTenant();
        return jdbc.query("SELECT id,display_name,account_jid,phone_normalized,last_sync_time,status FROM wa_account WHERE tenant_id=? AND deleted=0 ORDER BY id",
                (rs,row)->new AccountView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),local(rs,"last_sync_time"),rs.getInt(6)),tenant);
    }

    public List<BindingView> pendingBindings() {
        long tenant=requiredTenant();
        return pendingBindings(tenant);
    }

    @Transactional
    public List<BindingView> refreshBindingCandidates() {
        long tenant = requiredTenant();
        Map<String,Set<Long>> customersByPhone = customerIdsByPhone(tenant);
        List<Map<String,Object>> contacts = jdbc.queryForList("""
                SELECT id,phone_normalized FROM wa_contact
                WHERE tenant_id=? AND deleted=0 AND phone_normalized IS NOT NULL AND phone_normalized<>''
                """, tenant);
        jdbc.update("""
                UPDATE wa_customer_binding SET deleted=1,update_time=NOW(3)
                WHERE tenant_id=? AND status='PENDING' AND deleted=0
                """, tenant);
        for (Map<String,Object> contact : contacts) {
            long contactId = ((Number) contact.get("id")).longValue();
            String phone = digits((String) contact.get("phone_normalized"));
            Set<Long> matches = phone == null ? Set.of() : customersByPhone.getOrDefault(phone, Set.of());
            if (matches.size() == 1) {
                upsertExactPhoneCandidate(tenant, contactId, matches.iterator().next());
            }
        }
        return pendingBindings(tenant);
    }

    private List<BindingView> pendingBindings(long tenant) {
        return jdbc.query("""
                SELECT b.id,b.wa_contact_id,COALESCE(c.business_name,c.display_name,c.push_name),c.phone_normalized,
                  b.customer_id,cu.name,b.match_method,b.status,b.create_time
                FROM wa_customer_binding b JOIN wa_contact c ON c.id=b.wa_contact_id AND c.tenant_id=b.tenant_id
                JOIN crm_customer cu ON cu.id=b.customer_id AND cu.tenant_id=b.tenant_id
                WHERE b.tenant_id=? AND b.deleted=0 AND c.deleted=0 AND cu.deleted=0 AND b.status='PENDING'
                ORDER BY b.create_time DESC
                """,(rs,row)->new BindingView(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),
                rs.getLong(5),rs.getString(6),rs.getString(7),rs.getString(8),local(rs,"create_time")),tenant);
    }

    @Transactional
    public void decideBinding(Long id, BindingDecision decision, Long userId) {
        long tenant=requiredTenant();
        List<Map<String,Object>> binding=jdbc.queryForList("""
                SELECT customer_id,wa_contact_id FROM wa_customer_binding
                WHERE id=? AND tenant_id=? AND status='PENDING' AND deleted=0
                """,id,tenant);
        if(binding.size()!=1)throw new IllegalStateException("绑定候选不存在或已处理");
        int count=jdbc.update("""
                UPDATE wa_customer_binding SET status=?,confirmed_by=?,confirmed_at=NOW(3),note=?
                WHERE id=? AND tenant_id=? AND status='PENDING' AND deleted=0
                """,decision.status(),userId,decision.note(),id,tenant);
        if(count!=1)throw new IllegalStateException("绑定候选不存在或已处理");
        if("CONFIRMED".equals(decision.status())) {
            analysisService.enqueueForCustomer(tenant,((Number)binding.get(0).get("customer_id")).longValue(),
                    ((Number)binding.get(0).get("wa_contact_id")).longValue());
        }
    }

    public PageResult<IssueChatView> issueChats(int page, int size, String status, String mediaType, Long accountId) {
        long tenant = requiredTenant();
        int safePage = Math.max(1,page), safeSize = Math.min(100,Math.max(1,size));
        StringBuilder where = issueWhere(status, mediaType, accountId);
        List<Object> args = issueArgs(tenant, status, mediaType, accountId);
        String groupKey = "COALESCE(NULLIF(wc.phone_normalized,''),NULLIF(c.conversation_jid,''),CONCAT('#',i.conversation_id))";
        long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT 1 FROM wa_collection_issue i
                  LEFT JOIN wa_conversation c ON c.id=i.conversation_id AND c.tenant_id=i.tenant_id
                  LEFT JOIN wa_contact wc ON wc.id=c.contact_id AND wc.tenant_id=i.tenant_id AND wc.deleted=0
                """+where+" GROUP BY i.account_id,"+groupKey+") grouped_chats",Long.class,args.toArray());
        args.add(safeSize); args.add((safePage-1)*safeSize);
        List<IssueChatView> records = jdbc.query("""
                SELECT i.account_id,MAX(i.conversation_id) conversation_id,MAX(c.title) conversation_title,
                  MAX(b.customer_id) customer_id,MAX(cu.name) customer_name,MAX(c.conversation_jid) conversation_jid,
                  MAX(wc.phone_normalized) phone_normalized,
                  COUNT(*) issue_count,SUM(i.media_type='IMAGE') image_count,SUM(i.media_type='VIDEO') video_count,
                  SUM(i.media_type IN ('AUDIO','VOICE')) audio_count,SUM(i.status='OPEN') open_count,
                  SUM(i.status='RESOLVED') resolved_count,MAX(m.sent_at) latest_message_time,
                  MAX(i.last_detected_at) last_detected_at
                FROM wa_collection_issue i
                LEFT JOIN wa_conversation c ON c.id=i.conversation_id AND c.tenant_id=i.tenant_id
                LEFT JOIN wa_contact wc ON wc.id=c.contact_id AND wc.tenant_id=i.tenant_id AND wc.deleted=0
                LEFT JOIN wa_message m ON m.id=i.message_id AND m.tenant_id=i.tenant_id
                LEFT JOIN wa_customer_binding b ON b.wa_contact_id=c.contact_id AND b.tenant_id=i.tenant_id AND b.status='CONFIRMED' AND b.deleted=0
                LEFT JOIN crm_customer cu ON cu.id=b.customer_id AND cu.tenant_id=i.tenant_id AND cu.deleted=0
                """+where+" GROUP BY i.account_id,"+groupKey+" ORDER BY latest_message_time DESC,last_detected_at DESC LIMIT ? OFFSET ?",
                (rs,row)->new IssueChatView(rs.getLong("account_id"),nullableLong(rs,"conversation_id"),rs.getString("conversation_title"),
                        nullableLong(rs,"customer_id"),rs.getString("customer_name"),rs.getString("conversation_jid"),rs.getString("phone_normalized"),
                        rs.getLong("issue_count"),rs.getLong("image_count"),rs.getLong("video_count"),rs.getLong("audio_count"),
                        rs.getLong("open_count"),rs.getLong("resolved_count"),local(rs,"latest_message_time"),local(rs,"last_detected_at")),args.toArray());
        return PageResult.of(records,total,safeSize,safePage);
    }

    public PageResult<IssueView> issues(int page, int size, String status, String mediaType, Long accountId,
                                        String phoneNormalized, String conversationJid, Long conversationId) {
        long tenant = requiredTenant();
        int safePage = Math.max(1,page), safeSize = Math.min(100,Math.max(1,size));
        StringBuilder where = issueWhere(status, mediaType, accountId);
        List<Object> args = issueArgs(tenant, status, mediaType, accountId);
        if (phoneNormalized != null && !phoneNormalized.isBlank()) { where.append(" AND wc.phone_normalized=?"); args.add(digits(phoneNormalized)); }
        else if (conversationJid != null && !conversationJid.isBlank()) { where.append(" AND c.conversation_jid=?"); args.add(conversationJid); }
        else if (conversationId != null) { where.append(" AND i.conversation_id=?"); args.add(conversationId); }
        String joins = " LEFT JOIN wa_conversation c ON c.id=i.conversation_id AND c.tenant_id=i.tenant_id"+
                " LEFT JOIN wa_contact wc ON wc.id=c.contact_id AND wc.tenant_id=i.tenant_id AND wc.deleted=0";
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM wa_collection_issue i"+joins+where,Long.class,args.toArray());
        args.add(safeSize); args.add((safePage-1)*safeSize);
        List<IssueView> records = jdbc.query("""
                SELECT i.id,i.account_id,i.conversation_id,c.title,b.customer_id,cu.name AS customer_name,c.conversation_jid,i.message_id,m.sent_at,m.direction,
                  i.issue_type,i.status,i.severity,i.media_type,i.occurrence_count,i.first_detected_at,i.last_detected_at,i.resolved_at
                FROM wa_collection_issue i LEFT JOIN wa_conversation c ON c.id=i.conversation_id AND c.tenant_id=i.tenant_id
                LEFT JOIN wa_contact wc ON wc.id=c.contact_id AND wc.tenant_id=i.tenant_id AND wc.deleted=0
                LEFT JOIN wa_message m ON m.id=i.message_id AND m.tenant_id=i.tenant_id
                LEFT JOIN wa_customer_binding b ON b.wa_contact_id=c.contact_id AND b.tenant_id=i.tenant_id AND b.status='CONFIRMED' AND b.deleted=0
                LEFT JOIN crm_customer cu ON cu.id=b.customer_id AND cu.tenant_id=i.tenant_id AND cu.deleted=0
                """+where+" ORDER BY i.last_detected_at DESC LIMIT ? OFFSET ?", (rs,row)->new IssueView(
                rs.getLong("id"),rs.getLong("account_id"),nullableLong(rs,"conversation_id"),rs.getString("title"),
                nullableLong(rs,"customer_id"),rs.getString("customer_name"),
                rs.getString("conversation_jid"),nullableLong(rs,"message_id"),local(rs,"sent_at"),rs.getString("direction"),
                rs.getString("issue_type"),rs.getString("status"),rs.getString("severity"),rs.getString("media_type"),
                rs.getInt("occurrence_count"),local(rs,"first_detected_at"),local(rs,"last_detected_at"),local(rs,"resolved_at")),args.toArray());
        return PageResult.of(records,total,safeSize,safePage);
    }

    public PageResult<ArchiveChatView> archiveChats(int page, int size, Long accountId, String keyword) {
        long tenant = requiredTenant();
        int safePage = Math.max(1, page), safeSize = Math.min(100, Math.max(1, size));
        String identity = "COALESCE(NULLIF(wc.phone_normalized,''),c.conversation_jid)";
        StringBuilder eligibleWhere = new StringBuilder("""
                WHERE c.tenant_id=? AND c.deleted=0 AND c.conversation_type='DIRECT'
                """);
        List<Object> eligibleArgs = new ArrayList<>();
        eligibleArgs.add(tenant);
        if (accountId != null) {
            eligibleWhere.append(" AND c.account_id=?");
            eligibleArgs.add(accountId);
        }
        String search = blankToNull(keyword);
        if (search != null) {
            eligibleWhere.append(" AND (wc.phone_normalized LIKE ? OR wc.display_name LIKE ? OR wc.business_name LIKE ? OR c.title LIKE ?)");
            String like = "%" + search + "%";
            String phoneLike = digits(search) == null ? like : "%" + digits(search) + "%";
            eligibleArgs.add(phoneLike); eligibleArgs.add(like); eligibleArgs.add(like); eligibleArgs.add(like);
        }
        String from = " FROM wa_conversation c LEFT JOIN wa_contact wc ON wc.id=c.contact_id AND wc.tenant_id=c.tenant_id AND wc.deleted=0 ";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT c.account_id," + identity + " identity_key" + from +
                eligibleWhere + " GROUP BY c.account_id," + identity + ") grouped", Long.class, eligibleArgs.toArray());

        String cte = """
                WITH eligible AS (
                  SELECT c.id conversation_id,c.account_id,
                    COALESCE(NULLIF(wc.phone_normalized,''),c.conversation_jid) identity_key,
                    COALESCE(NULLIF(wc.business_name,''),NULLIF(wc.display_name,''),NULLIF(c.title,''),
                             NULLIF(wc.phone_normalized,''),c.conversation_jid) display_name,
                    wc.phone_normalized
                  FROM wa_conversation c
                  LEFT JOIN wa_contact wc ON wc.id=c.contact_id AND wc.tenant_id=c.tenant_id AND wc.deleted=0
                """ + eligibleWhere + """
                ), ranked AS (
                  SELECT e.account_id,e.identity_key,e.display_name,e.phone_normalized,
                    m.id,m.sent_at,m.direction,m.message_type,m.text_content,
                    COUNT(*) OVER (PARTITION BY e.account_id,e.identity_key) message_count,
                    ROW_NUMBER() OVER (PARTITION BY e.account_id,e.identity_key ORDER BY m.sent_at DESC,m.id DESC) row_no
                  FROM eligible e
                  JOIN wa_message m ON m.conversation_id=e.conversation_id AND m.tenant_id=? AND m.deleted=0
                )
                SELECT account_id,identity_key,display_name,phone_normalized,message_count,id,last_message_at,
                       direction,message_type,text_content
                FROM (SELECT account_id,identity_key,display_name,phone_normalized,message_count,id,
                             sent_at last_message_at,direction,message_type,text_content,row_no
                      FROM ranked) latest
                WHERE row_no=1
                ORDER BY last_message_at DESC,id DESC LIMIT ? OFFSET ?
                """;
        List<Object> args = new ArrayList<>(eligibleArgs);
        args.add(tenant); args.add(safeSize); args.add((safePage - 1) * safeSize);
        List<ArchiveChatView> records = jdbc.query(cte, (rs,row) -> new ArchiveChatView(
                rs.getLong("account_id"), rs.getString("identity_key"), rs.getString("display_name"),
                rs.getString("phone_normalized"), rs.getLong("message_count"), nullableLong(rs,"id"),
                local(rs,"last_message_at"), rs.getString("direction"), rs.getString("message_type"),
                rs.getString("text_content")), args.toArray());
        return PageResult.of(records, total == null ? 0 : total, safeSize, safePage);
    }

    public PageResult<ArchiveMessageView> archiveMessages(int page, int size, Long accountId, String identityKey) {
        long tenant = requiredTenant();
        int safePage = Math.max(1, page), safeSize = Math.min(100, Math.max(1, size));
        String identity = blankToNull(identityKey);
        if (accountId == null || identity == null) throw new IllegalArgumentException("缺少WhatsApp聊天标识");
        if (!exists("SELECT COUNT(*) FROM wa_account WHERE tenant_id=? AND id=? AND deleted=0", tenant, accountId))
            throw new IllegalArgumentException("WhatsApp账号不存在");
        String scope = """
                FROM wa_message m
                JOIN wa_conversation c ON c.id=m.conversation_id AND c.tenant_id=m.tenant_id AND c.deleted=0 AND c.conversation_type='DIRECT'
                LEFT JOIN wa_contact wc ON wc.id=c.contact_id AND wc.tenant_id=c.tenant_id AND wc.deleted=0
                WHERE m.tenant_id=? AND m.account_id=? AND m.deleted=0
                  AND COALESCE(NULLIF(wc.phone_normalized,''),c.conversation_jid)=?
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + scope, Long.class, tenant, accountId, identity);
        List<ArchiveMessageView> messages = jdbc.query("""
                SELECT m.id,m.sent_at,m.direction,m.message_type,m.text_content,m.status,m.is_starred
                """ + scope + " ORDER BY m.sent_at DESC,m.id DESC LIMIT ? OFFSET ?", (rs,row) -> new ArchiveMessageView(
                rs.getLong("id"), local(rs,"sent_at"), rs.getString("direction"), rs.getString("message_type"),
                rs.getString("text_content"), rs.getString("status"), rs.getBoolean("is_starred"), new ArrayList<>()),
                tenant, accountId, identity, safeSize, (safePage - 1) * safeSize);
        if (!messages.isEmpty()) {
            List<Long> messageIds = messages.stream().map(ArchiveMessageView::id).toList();
            String placeholders = String.join(",", Collections.nCopies(messageIds.size(), "?"));
            List<Object> mediaArgs = new ArrayList<>(); mediaArgs.add(tenant); mediaArgs.addAll(messageIds);
            Map<Long,List<ArchiveMediaView>> mediaByMessage = new HashMap<>();
            jdbc.query("""
                    SELECT mm.message_id,mm.id,mm.file_id,mm.media_type,mm.mime_type,mm.original_name,mm.file_size,
                           mm.caption,mm.duration_ms,mm.width,mm.height,mm.download_status,
                           (SELECT i.issue_type FROM wa_collection_issue i
                            WHERE i.tenant_id=mm.tenant_id AND i.message_id=mm.message_id
                              AND (i.media_id=mm.id OR i.media_id IS NULL) AND i.status='OPEN' AND i.deleted=0
                            ORDER BY i.last_detected_at DESC LIMIT 1) issue_type
                    FROM wa_message_media mm
                    WHERE mm.tenant_id=? AND mm.deleted=0 AND mm.message_id IN (""" + placeholders + ") ORDER BY mm.id", rs -> {
                long messageId = rs.getLong("message_id");
                ArchiveMediaView media = new ArchiveMediaView(rs.getLong("id"), nullableLong(rs,"file_id"),
                        rs.getString("media_type"), rs.getString("mime_type"), rs.getString("original_name"),
                        nullableLong(rs,"file_size"), rs.getString("caption"), nullableLong(rs,"duration_ms"),
                        nullableInteger(rs,"width"), nullableInteger(rs,"height"), rs.getString("download_status"),
                        rs.getString("issue_type"));
                mediaByMessage.computeIfAbsent(messageId, ignored -> new ArrayList<>()).add(media);
            }, mediaArgs.toArray());
            Set<String> mediaTypes = Set.of("IMAGE","VIDEO","AUDIO","VOICE","DOCUMENT","STICKER");
            messages = messages.stream().map(message -> {
                List<ArchiveMediaView> media = mediaByMessage.getOrDefault(message.id(), List.of());
                if (media.isEmpty() && mediaTypes.contains(message.messageType())) {
                    media = List.of(new ArchiveMediaView(null,null,message.messageType(),null,null,0L,null,
                            null,null,null,"MISSING","MEDIA_ITEM_MISSING"));
                }
                return new ArchiveMessageView(message.id(),message.sentAt(),message.direction(),message.messageType(),
                        message.textContent(),message.status(),message.starred(),media);
            }).sorted(Comparator.comparing(ArchiveMessageView::sentAt).thenComparing(ArchiveMessageView::id)).toList();
        }
        return PageResult.of(messages, total == null ? 0 : total, safeSize, safePage);
    }

    private StringBuilder issueWhere(String status, String mediaType, Long accountId) {
        StringBuilder where = new StringBuilder(" WHERE i.tenant_id=? AND i.deleted=0");
        if (status != null && !status.isBlank()) where.append(" AND i.status=?");
        if (mediaType != null && !mediaType.isBlank()) where.append(" AND i.media_type=?");
        if (accountId != null) where.append(" AND i.account_id=?");
        return where;
    }

    private List<Object> issueArgs(long tenant, String status, String mediaType, Long accountId) {
        List<Object> args = new ArrayList<>();
        args.add(tenant);
        if (status != null && !status.isBlank()) args.add(status);
        if (mediaType != null && !mediaType.isBlank()) args.add(mediaType);
        if (accountId != null) args.add(accountId);
        return args;
    }

    @Transactional
    public ScanJobView requestScan(Long accountId, String scopeType, String requestedPhone,
                                   String requestedConversationJid, Long userId) {
        long tenant = requiredTenant();
        if (!exists("SELECT COUNT(*) FROM wa_account WHERE tenant_id=? AND id=? AND status=1 AND deleted=0",tenant,accountId))
            throw new IllegalArgumentException("WhatsApp账号不存在");
        String scope = normalizeScanScope(scopeType);
        String targetPhone = null, targetJid = null;
        if ("CONTACT".equals(scope)) {
            String phone = digits(requestedPhone), jid = blankToNull(requestedConversationJid);
            if (phone == null && jid == null) throw new IllegalArgumentException("客户扫描缺少目标号码或会话标识");
            StringBuilder sql = new StringBuilder("""
                    SELECT wc.phone_normalized,c.conversation_jid
                    FROM wa_conversation c JOIN wa_contact wc ON wc.id=c.contact_id AND wc.tenant_id=c.tenant_id
                    WHERE c.tenant_id=? AND c.account_id=? AND c.deleted=0 AND wc.deleted=0
                    """);
            List<Object> targetArgs = new ArrayList<>(); targetArgs.add(tenant); targetArgs.add(accountId);
            if (phone != null) { sql.append(" AND wc.phone_normalized=?"); targetArgs.add(phone); }
            if (jid != null) { sql.append(" AND c.conversation_jid=?"); targetArgs.add(jid); }
            List<Map<String,Object>> matches = jdbc.queryForList(sql+" LIMIT 1",targetArgs.toArray());
            if (matches.isEmpty()) throw new IllegalArgumentException("目标客户不属于当前WhatsApp账号");
            targetPhone = phone != null ? phone : digits((String) matches.get(0).get("phone_normalized"));
            targetJid = jid != null ? jid : blankToNull((String) matches.get(0).get("conversation_jid"));
        }
        List<Long> pending;
        if ("ACCOUNT".equals(scope)) {
            pending = jdbc.query("SELECT id FROM wa_scan_job WHERE tenant_id=? AND account_id=? AND scope_type='ACCOUNT' AND status IN ('PENDING','CLAIMED') AND deleted=0 ORDER BY requested_at LIMIT 1",
                    (rs,row)->rs.getLong(1),tenant,accountId);
        } else if (targetPhone != null) {
            pending = jdbc.query("SELECT id FROM wa_scan_job WHERE tenant_id=? AND account_id=? AND scope_type='CONTACT' AND target_phone_normalized=? AND status IN ('PENDING','CLAIMED') AND deleted=0 ORDER BY requested_at LIMIT 1",
                    (rs,row)->rs.getLong(1),tenant,accountId,targetPhone);
        } else {
            pending = jdbc.query("SELECT id FROM wa_scan_job WHERE tenant_id=? AND account_id=? AND scope_type='CONTACT' AND target_conversation_jid=? AND status IN ('PENDING','CLAIMED') AND deleted=0 ORDER BY requested_at LIMIT 1",
                    (rs,row)->rs.getLong(1),tenant,accountId,targetJid);
        }
        Long id;
        if (!pending.isEmpty()) id=pending.get(0); else {
            KeyHolder holder=new GeneratedKeyHolder();
            String finalTargetPhone = targetPhone, finalTargetJid = targetJid;
            jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO wa_scan_job(tenant_id,account_id,scope_type,target_phone_normalized,target_conversation_jid,status,requested_by,requested_at) VALUES(?,?,?,?,?,'PENDING',?,NOW(3))",Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1,tenant);ps.setLong(2,accountId);ps.setString(3,scope);ps.setString(4,finalTargetPhone);ps.setString(5,finalTargetJid);
                if(userId==null)ps.setNull(6,java.sql.Types.BIGINT);else ps.setLong(6,userId);return ps;},holder);
            id=Objects.requireNonNull(holder.getKey()).longValue();
        }
        return scanJob(tenant,id);
    }

    @Transactional
    public ScanJobView claimScan(CollectorPrincipal principal) {
        requireScope(principal,"scan:poll");
        List<Long> ids=jdbc.query("SELECT id FROM wa_scan_job WHERE tenant_id=? AND account_id=? AND status='PENDING' AND deleted=0 ORDER BY requested_at LIMIT 1 FOR UPDATE",
                (rs,row)->rs.getLong(1),principal.tenantId(),principal.accountId());
        if(ids.isEmpty()) return null;
        jdbc.update("UPDATE wa_scan_job SET status='CLAIMED',claimed_at=NOW(3) WHERE id=? AND tenant_id=?",ids.get(0),principal.tenantId());
        return scanJob(principal.tenantId(),ids.get(0));
    }

    @Transactional
    public ScanJobView completeScan(CollectorPrincipal principal, Long jobId, ScanJobCompleteRequest request) {
        requireScope(principal,"scan:poll");
        if(!Set.of("SUCCEEDED","FAILED").contains(request.status())) throw new IllegalArgumentException("非法重扫状态");
        BatchRow completedBatch=request.batchNo()==null?null:batch(principal,request.batchNo());
        Long batchId=completedBatch==null?null:completedBatch.id();
        if (completedBatch != null && !exists("""
                SELECT COUNT(*) FROM wa_scan_job
                WHERE id=? AND tenant_id=? AND account_id=? AND scope_type=?
                  AND (target_phone_normalized <=> ?) AND (target_conversation_jid <=> ?)
                """,jobId,principal.tenantId(),principal.accountId(),completedBatch.scanScopeType(),
                completedBatch.targetPhoneNormalized(),completedBatch.targetConversationJid()))
            throw new IllegalArgumentException("扫描任务与导入批次范围不一致");
        int count=jdbc.update("UPDATE wa_scan_job SET status=?,completed_at=NOW(3),result_batch_id=?,error_summary=? WHERE id=? AND tenant_id=? AND account_id=? AND status='CLAIMED'",
                request.status(),batchId,request.errorSummary(),jobId,principal.tenantId(),principal.accountId());
        if(count!=1) throw new IllegalStateException("重扫任务状态已变化");
        return scanJob(principal.tenantId(),jobId);
    }

    public ScanJobView latestScan() {
        long tenant=requiredTenant();
        List<Long> ids=jdbc.query("SELECT id FROM wa_scan_job WHERE tenant_id=? AND deleted=0 ORDER BY requested_at DESC LIMIT 1",(rs,row)->rs.getLong(1),tenant);
        return ids.isEmpty()?null:scanJob(tenant,ids.get(0));
    }

    private ScanJobView scanJob(long tenant,long id){return jdbc.queryForObject("""
            SELECT j.id,j.account_id,a.display_name,j.scope_type,j.target_phone_normalized,j.target_conversation_jid,
              j.status,j.requested_at,j.claimed_at,j.completed_at,j.result_batch_id,j.error_summary
            FROM wa_scan_job j JOIN wa_account a ON a.id=j.account_id AND a.tenant_id=j.tenant_id WHERE j.tenant_id=? AND j.id=?
            """,(rs,row)->new ScanJobView(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),
                    rs.getString(7),local(rs,"requested_at"),local(rs,"claimed_at"),local(rs,"completed_at"),nullableLong(rs,"result_batch_id"),rs.getString("error_summary")),tenant,id);}

    private void createExactPhoneCandidate(long tenant, long contactId, String phone) {
        String normalized=digits(phone); if(normalized==null||normalized.length()<6)return;
        Set<Long> matches=customerIdsByPhone(tenant).getOrDefault(normalized,Set.of());
        if(matches.size()!=1)return;
        upsertExactPhoneCandidate(tenant,contactId,matches.iterator().next());
    }

    private Map<String,Set<Long>> customerIdsByPhone(long tenant) {
        List<Map<String,Object>> rows=jdbc.queryForList("""
                SELECT cp.customer_id,cp.phone,c.country_code
                FROM crm_customer_phone cp
                JOIN crm_customer c ON c.id=cp.customer_id AND c.tenant_id=cp.tenant_id AND c.deleted=0
                WHERE cp.tenant_id=? AND cp.deleted=0
                """,tenant);
        Map<String,Set<Long>> result=new HashMap<>();
        for(Map<String,Object> row:rows) {
            long customerId=((Number)row.get("customer_id")).longValue();
            for(String variant:WhatsappPhoneMatcher.customerPhoneVariants((String)row.get("country_code"),(String)row.get("phone")))
                if(variant.length()>=6)result.computeIfAbsent(variant,ignored->new HashSet<>()).add(customerId);
        }
        return result;
    }

    private void upsertExactPhoneCandidate(long tenant,long contactId,long customerId) {
        jdbc.update("""
                INSERT INTO wa_customer_binding(tenant_id,wa_contact_id,customer_id,match_method,confidence,status,note)
                VALUES(?,?,?,'EXACT_PHONE',1.0000,'PENDING','国家区号与手机号唯一精确匹配，待人工确认')
                ON DUPLICATE KEY UPDATE customer_id=IF(status='PENDING',VALUES(customer_id),customer_id),
                  match_method=IF(status='PENDING','EXACT_PHONE',match_method),confidence=IF(status='PENDING',1.0000,confidence),
                  note=IF(status='PENDING',VALUES(note),note),deleted=IF(status='PENDING',0,deleted),update_time=NOW(3)
                """,tenant,contactId,customerId);
    }
    private void ensureBinding(long tenant,long fileId,long messageId){if(!exists("SELECT COUNT(*) FROM file_business_bind WHERE tenant_id=? AND file_id=? AND business_type='whatsapp_message' AND business_id=? AND bind_role='attachment' AND deleted=0",tenant,fileId,messageId))jdbc.update("INSERT INTO file_business_bind(file_id,business_type,business_id,bind_role,sort,is_primary,tenant_id,deleted) VALUES(?,'whatsapp_message',?,'attachment',0,0,?,0)",fileId,messageId,tenant);}
    private BatchRow batch(CollectorPrincipal p,String no){List<BatchRow> rows=jdbc.query("SELECT id,status,scan_scope_type,target_phone_normalized,target_conversation_jid FROM wa_import_batch WHERE tenant_id=? AND account_id=? AND batch_no=? AND deleted=0",(rs,row)->new BatchRow(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),p.tenantId(),p.accountId(),no);if(rows.size()!=1)throw new IllegalArgumentException("导入批次不存在");return rows.get(0);}
    private BatchRow runningBatch(CollectorPrincipal p,String no){BatchRow row=batch(p,no);if(!"RUNNING".equals(row.status()))throw new IllegalStateException("导入批次已结束");return row;}
    private Long contactId(CollectorPrincipal p,String jid){Long id=nullableContactId(p,jid);if(id==null)throw new IllegalArgumentException("联系人不存在: "+jid);return id;}
    private Long nullableContactId(CollectorPrincipal p,String jid){if(jid==null||jid.isBlank())return null;List<Long> ids=jdbc.query("SELECT id FROM wa_contact WHERE tenant_id=? AND account_id=? AND contact_jid=? AND deleted=0",(rs,row)->rs.getLong(1),p.tenantId(),p.accountId(),jid);return ids.isEmpty()?null:ids.get(0);}
    private Long requiredConversationId(CollectorPrincipal p,String jid){Long id=nullableConversationId(p,jid);if(id==null)throw new IllegalArgumentException("会话不存在: "+jid);return id;}
    private Long nullableConversationId(CollectorPrincipal p,String jid){if(jid==null||jid.isBlank())return null;List<Long> ids=jdbc.query("SELECT id FROM wa_conversation WHERE tenant_id=? AND account_id=? AND conversation_jid=? AND deleted=0",(rs,row)->rs.getLong(1),p.tenantId(),p.accountId(),jid);return ids.isEmpty()?null:ids.get(0);}
    private Long requiredMessageId(CollectorPrincipal p,String hash){Long id=nullableMessageId(p,hash);if(id==null)throw new IllegalArgumentException("消息不存在");return id;}
    private Long nullableMessageId(CollectorPrincipal p,String hash){if(hash==null)return null;List<Long> ids=jdbc.query("SELECT id FROM wa_message WHERE tenant_id=? AND account_id=? AND logical_key_hash=? AND deleted=0",(rs,row)->rs.getLong(1),p.tenantId(),p.accountId(),hex(hash));return ids.isEmpty()?null:ids.get(0);}
    private Long firstMediaId(long tenant,long messageId){List<Long> ids=jdbc.query("SELECT id FROM wa_message_media WHERE tenant_id=? AND message_id=? AND deleted=0 ORDER BY id LIMIT 1",(rs,row)->rs.getLong(1),tenant,messageId);return ids.isEmpty()?null:ids.get(0);}
    private void addBatchCounts(long tenant,long id,Counter c){jdbc.update("UPDATE wa_import_batch SET inserted_count=inserted_count+?,updated_count=updated_count+? WHERE tenant_id=? AND id=?",c.inserted,c.updated,tenant,id);}
    private void requireScope(CollectorPrincipal p,String scope){if(!p.scopes().contains(scope))throw new AccessDeniedException("Collector scope不足");}
    private long requiredTenant(){Long value=TenantContext.getTenantId();if(value==null)throw new AccessDeniedException("缺少租户上下文");return value;}
    private boolean exists(String sql,Object...args){Long value=jdbc.queryForObject(sql,Long.class,args);return value!=null&&value>0;}
    private Long singleLong(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    private String json(Object value){if(value==null)return null;try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException("JSON字段无法序列化",e);}}
    private byte[] digest(MultipartFile file){try(InputStream in=file.getInputStream()){MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] buf=new byte[8192];for(int n;(n=in.read(buf))>=0;)if(n>0)md.update(buf,0,n);return md.digest();}catch(Exception e){throw new IllegalArgumentException("媒体读取失败",e);}}
    private static byte[] sha256(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    private static byte[] hex(String value){try{byte[] bytes=HexFormat.of().parseHex(value);if(bytes.length!=32)throw new IllegalArgumentException("哈希长度必须为32字节");return bytes;}catch(IllegalArgumentException e){throw new IllegalArgumentException("无效SHA-256",e);}}
    private static byte[] hexOrNull(String value){return value==null||value.isBlank()?null:hex(value);}
    private static String digits(String value){if(value==null)return null;String d=value.trim().replaceFirst("^00","+").replaceAll("[^0-9]","");return d.isBlank()?null:d;}
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String normalizeScanScope(String value){String scope=value==null||value.isBlank()?"ACCOUNT":value.toUpperCase(Locale.ROOT);if(!Set.of("ACCOUNT","CONTACT").contains(scope))throw new IllegalArgumentException("非法扫描范围");return scope;}
    private static String randomToken(int bytes){byte[] raw=new byte[bytes];RANDOM.nextBytes(raw);return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);}
    private static int bool(Boolean value){return Boolean.TRUE.equals(value)?1:0;} private static long zero(Long v){return v==null?0:v;} private static int zero(Integer v){return v==null?0:v;}
    private static String normalizedDirection(String d){String v=d.toUpperCase(Locale.ROOT);if(!Set.of("INBOUND","OUTBOUND","SYSTEM").contains(v))throw new IllegalArgumentException("非法消息方向");return v;}
    private static String safeRelativePath(String path){if(path==null)return null;String v=path.replace('\\','/');if(v.startsWith("/")||v.contains("../")||v.contains("/Users/"))throw new IllegalArgumentException("媒体路径必须是安全相对路径");return v.length()>500?v.substring(0,500):v;}
    private static String safeName(String preferred,String fallback){String value=(preferred==null||preferred.isBlank())?fallback:preferred;if(value==null)return "whatsapp-media";value=value.replace('\\','/');value=value.substring(value.lastIndexOf('/')+1);return value.length()>255?value.substring(value.length()-255):value;}
    private static String fileType(String mime){return mime.split("/")[0];} private static String extension(String name){int i=name.lastIndexOf('.');return i<0?null:name.substring(i+1);}
    private static String normalizeMediaStatus(String value){String v=value.toUpperCase(Locale.ROOT);if("ARCHIVED".equals(v))return "AVAILABLE";if(!Set.of("METADATA_ONLY","AVAILABLE","MISSING","FAILED","IMPORTED").contains(v))return "METADATA_ONLY";return v;}
    private static long number(Map<String,Object> row,String key){Object v=row.get(key);return v==null?0:((Number)v).longValue();}
    private static LocalDateTime asLocalDateTime(Object value){if(value==null)return null;if(value instanceof LocalDateTime local)return local;if(value instanceof java.sql.Timestamp timestamp)return timestamp.toLocalDateTime();throw new IllegalArgumentException("不支持的时间值");}
    private static Long nullableLong(java.sql.ResultSet rs,String name)throws java.sql.SQLException{long value=rs.getLong(name);return rs.wasNull()?null:value;}
    private static Integer nullableInteger(java.sql.ResultSet rs,String name)throws java.sql.SQLException{int value=rs.getInt(name);return rs.wasNull()?null:value;}
    private static LocalDateTime local(java.sql.ResultSet rs,String name)throws java.sql.SQLException{java.sql.Timestamp value=rs.getTimestamp(name);return value==null?null:value.toLocalDateTime();}
    private record BatchRow(Long id,String status,String scanScopeType,String targetPhoneNormalized,String targetConversationJid){}
    private static final class Counter{long inserted,updated;void add(boolean exists){if(exists)updated++;else inserted++;}ImportCounts result(){return new ImportCounts(inserted,updated);}}
}
