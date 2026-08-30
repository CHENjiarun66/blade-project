package com.blade.whatsapp.service;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.whatsapp.dto.WhatsappAnalysisDtos.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

import static com.blade.whatsapp.service.WhatsappContextRedactor.limit;
import static com.blade.whatsapp.service.WhatsappContextRedactor.sanitize;

@Service
@RequiredArgsConstructor
public class WhatsappAnalysisService {
    private static final int CONTEXT_DAYS = 90;
    private static final int MAX_MESSAGE_CHARS = 2000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public WorkerCredential createWorkerCredential(WorkerCredentialRequest request) {
        long tenant=requiredTenant();
        String prefix="waa_"+randomToken(8),secret=randomToken(32),scopes="whatsapp:analyze";
        KeyHolder holder=new GeneratedKeyHolder();
        jdbc.update(connection->{PreparedStatement ps=connection.prepareStatement("""
                INSERT INTO agent_key(tenant_id,name,key_prefix,key_hash,scopes,status)
                VALUES(?,?,?,?,?,1)
                """,Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,tenant);ps.setString(2,request.name());ps.setString(3,prefix);
            ps.setString(4,passwordEncoder.encode(secret));ps.setString(5,scopes);return ps;},holder);
        return new WorkerCredential(Objects.requireNonNull(holder.getKey()).longValue(),prefix+"."+secret,prefix,List.of(scopes));
    }

    @Transactional
    public int enqueueForBatch(long tenant, long batchId) {
        List<Map<String,Object>> customers = jdbc.queryForList("""
                SELECT DISTINCT b.customer_id,b.wa_contact_id
                FROM wa_message m
                JOIN wa_conversation c ON c.id=m.conversation_id AND c.tenant_id=m.tenant_id
                JOIN wa_customer_binding b ON b.wa_contact_id=c.contact_id AND b.tenant_id=m.tenant_id
                  AND b.status='CONFIRMED' AND b.deleted=0
                WHERE m.tenant_id=? AND m.first_seen_batch_id=? AND m.deleted=0
                """, tenant, batchId);
        int count = 0;
        for (Map<String,Object> row : customers) {
            count += enqueue(tenant, number(row,"customer_id"), number(row,"wa_contact_id"), batchId);
        }
        return count;
    }

    @Transactional
    public int enqueueForCustomer(long tenant, long customerId, long contactId) {
        Long valid = jdbc.queryForObject("""
                SELECT COUNT(*) FROM wa_customer_binding
                WHERE tenant_id=? AND customer_id=? AND wa_contact_id=? AND status='CONFIRMED' AND deleted=0
                """, Long.class, tenant, customerId, contactId);
        if (valid == null || valid != 1) throw new IllegalArgumentException("客户绑定尚未确认");
        return enqueue(tenant, customerId, contactId, null);
    }

    private int enqueue(long tenant, long customerId, long contactId, Long batchId) {
        ContextStamp stamp = contextStamp(tenant, customerId, contactId);
        if (stamp.messageCount() == 0) return 0;
        byte[] version = sha256(tenant+":"+customerId+":"+stamp.lastMessageId()+":"+stamp.lastMessageAt()+":"+
                stamp.lastMessageUpdate()+":"+stamp.messageCount()+":"+stamp.orderCount()+":"+stamp.lastOrderUpdate());
        return jdbc.update("""
                INSERT IGNORE INTO wa_analysis_job(tenant_id,customer_id,wa_contact_id,trigger_batch_id,
                  context_version_hash,status,priority,available_at)
                VALUES(?,?,?,?,?,'PENDING',100,NOW(3))
                """, tenant, customerId, contactId, batchId, version);
    }

    @Transactional
    public AnalysisJobContext claim(AgentPrincipal principal) {
        long tenant = principal.getTenantId();
        jdbc.update("""
                UPDATE wa_analysis_job SET status='FAILED',last_error_code='LEASE_EXHAUSTED',lease_until=NULL
                WHERE tenant_id=? AND status='CLAIMED' AND lease_until<NOW(3) AND attempt_count>=max_attempts AND deleted=0
                """, tenant);
        jdbc.update("""
                UPDATE wa_analysis_job SET status='PENDING',claimed_by_agent_key_id=NULL,lease_until=NULL,available_at=NOW(3)
                WHERE tenant_id=? AND status='CLAIMED' AND lease_until<NOW(3) AND attempt_count<max_attempts AND deleted=0
                """, tenant);
        List<Long> ids = jdbc.query("""
                SELECT id FROM wa_analysis_job
                WHERE tenant_id=? AND status='PENDING' AND available_at<=NOW(3) AND deleted=0
                ORDER BY priority ASC,available_at ASC,id ASC LIMIT 1 FOR UPDATE
                """, (rs,row)->rs.getLong(1), tenant);
        if (ids.isEmpty()) return null;
        long id = ids.get(0);
        int updated = jdbc.update("""
                UPDATE wa_analysis_job SET status='CLAIMED',attempt_count=attempt_count+1,
                  claimed_by_agent_key_id=?,lease_until=DATE_ADD(NOW(3),INTERVAL 10 MINUTE),last_error_code=NULL
                WHERE tenant_id=? AND id=? AND status='PENDING' AND deleted=0
                """, principal.getKeyId(), tenant, id);
        if (updated != 1) return null;
        return context(tenant, id, principal.getKeyId());
    }

    @Transactional
    public AnalysisResult complete(AgentPrincipal principal, long jobId, AnalysisCompleteRequest request) {
        AnalysisResult replay = completedResult(principal,jobId);
        if(replay!=null)return replay;
        JobRow job = claimedJob(principal, jobId);
        List<Long> requestedEvidence = request.evidenceMessageIds().stream().distinct().toList();
        Set<Long> allowed = new HashSet<>(claimedContextMessageIds(job));
        if (!allowed.containsAll(requestedEvidence)) throw new IllegalArgumentException("证据消息不属于当前分析上下文");
        List<Long> existing = jdbc.query("SELECT id FROM wa_customer_analysis WHERE tenant_id=? AND analysis_job_id=?",
                (rs,row)->rs.getLong(1), job.tenant(), job.id());
        if (!existing.isEmpty()) {
            Long recommendationId = jdbc.queryForObject("SELECT id FROM wa_followup_recommendation WHERE tenant_id=? AND analysis_id=?",
                    Long.class, job.tenant(), existing.get(0));
            return new AnalysisResult(existing.get(0), recommendationId, "SUCCEEDED");
        }
        KeyHolder analysisHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO wa_customer_analysis(tenant_id,analysis_job_id,customer_id,wa_contact_id,summary_text,
                      preferences_json,intent_stage,sentiment,churn_risk,recommended_followup_at,recommended_action,
                      confidence,evidence_message_ids,provider,model_name,prompt_version,analyzed_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            int i=1; ps.setLong(i++,job.tenant()); ps.setLong(i++,job.id()); ps.setLong(i++,job.customerId());
            ps.setLong(i++,job.contactId()); ps.setString(i++,request.summary()); ps.setString(i++,json(request.preferences()));
            ps.setString(i++,request.intentStage()); ps.setString(i++,request.sentiment()); ps.setString(i++,request.churnRisk());
            if(request.recommendedFollowupAt()==null) ps.setNull(i++,java.sql.Types.TIMESTAMP); else ps.setObject(i++,request.recommendedFollowupAt());
            ps.setString(i++,request.recommendedAction()); ps.setBigDecimal(i++,request.confidence());
            ps.setString(i++,json(requestedEvidence)); ps.setString(i++,request.provider()); ps.setString(i++,request.model());
            ps.setString(i,request.promptVersion()); return ps;
        }, analysisHolder);
        long analysisId = Objects.requireNonNull(analysisHolder.getKey()).longValue();
        KeyHolder recommendationHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps=connection.prepareStatement("""
                    INSERT INTO wa_followup_recommendation(tenant_id,analysis_id,customer_id,status,due_at,title,
                      recommended_action,confidence) VALUES(?,?,?,'PENDING',?,?,?,?)
                    """,Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,job.tenant());ps.setLong(2,analysisId);ps.setLong(3,job.customerId());
            if(request.recommendedFollowupAt()==null)ps.setNull(4,java.sql.Types.TIMESTAMP);else ps.setObject(4,request.recommendedFollowupAt());
            ps.setString(5,title(request));ps.setString(6,request.recommendedAction());ps.setBigDecimal(7,request.confidence());return ps;
        },recommendationHolder);
        jdbc.update("""
                UPDATE wa_analysis_job SET status='SUCCEEDED',completed_at=NOW(3),lease_until=NULL
                WHERE tenant_id=? AND id=? AND claimed_by_agent_key_id=? AND status='CLAIMED'
                """, job.tenant(),job.id(),principal.getKeyId());
        return new AnalysisResult(analysisId,Objects.requireNonNull(recommendationHolder.getKey()).longValue(),"SUCCEEDED");
    }

    @Transactional
    public void fail(AgentPrincipal principal, long jobId, AnalysisFailRequest request) {
        JobRow job=claimedJob(principal,jobId);
        boolean exhausted=job.attempts()>=job.maxAttempts();
        LocalDateTime retryAt=LocalDateTime.now().plusMinutes(Math.max(5,job.attempts()*5L));
        jdbc.update("""
                UPDATE wa_analysis_job SET status=?,available_at=?,lease_until=NULL,claimed_by_agent_key_id=NULL,last_error_code=?
                WHERE tenant_id=? AND id=? AND status='CLAIMED' AND claimed_by_agent_key_id=?
                """,exhausted?"FAILED":"PENDING",retryAt,request.errorCode(),job.tenant(),job.id(),principal.getKeyId());
    }

    public PageResult<InsightView> insights(int page,int size,String status) {
        long tenant=requiredTenant(); int safePage=Math.max(1,page),safeSize=Math.min(100,Math.max(1,size));
        String suffix=status==null||status.isBlank()?"":" AND r.status=?";
        Object[] base=status==null||status.isBlank()?new Object[]{tenant}:new Object[]{tenant,status};
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM wa_followup_recommendation r WHERE r.tenant_id=? AND r.deleted=0"+suffix,Long.class,base));
        List<Object> args=new ArrayList<>(Arrays.asList(base));args.add(safeSize);args.add((safePage-1)*safeSize);
        List<InsightView> records=jdbc.query("""
                SELECT r.id recommendation_id,a.id analysis_id,r.customer_id,c.name customer_name,r.status,r.due_at,
                  a.summary_text,a.preferences_json,a.intent_stage,a.sentiment,a.churn_risk,r.recommended_action,
                  r.confidence,a.evidence_message_ids,a.model_name,a.analyzed_at,r.handled_at,r.handle_note
                FROM wa_followup_recommendation r
                JOIN wa_customer_analysis a ON a.id=r.analysis_id AND a.tenant_id=r.tenant_id
                JOIN crm_customer c ON c.id=r.customer_id AND c.tenant_id=r.tenant_id AND c.deleted=0
                WHERE r.tenant_id=? AND r.deleted=0
                """+suffix+" ORDER BY (r.status='PENDING') DESC,(r.due_at IS NULL),r.due_at,r.id DESC LIMIT ? OFFSET ?",
                (rs,row)->new InsightView(rs.getLong("recommendation_id"),rs.getLong("analysis_id"),rs.getLong("customer_id"),
                        rs.getString("customer_name"),rs.getString("status"),local(rs,"due_at"),rs.getString("summary_text"),
                        map(rs.getString("preferences_json")),rs.getString("intent_stage"),rs.getString("sentiment"),
                        rs.getString("churn_risk"),rs.getString("recommended_action"),rs.getBigDecimal("confidence"),
                        longs(rs.getString("evidence_message_ids")),rs.getString("model_name"),local(rs,"analyzed_at"),
                        local(rs,"handled_at"),rs.getString("handle_note")),args.toArray());
        return PageResult.of(records,total,safeSize,safePage);
    }

    public List<EvidenceView> evidence(long analysisId) {
        long tenant=requiredTenant();
        List<String> values=jdbc.query("SELECT evidence_message_ids FROM wa_customer_analysis WHERE tenant_id=? AND id=?",
                (rs,row)->rs.getString(1),tenant,analysisId);
        if(values.size()!=1)throw new IllegalArgumentException("分析结果不存在");
        List<Long> ids=longs(values.get(0)); if(ids.isEmpty())return List.of();
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        List<Object> args=new ArrayList<>();args.add(tenant);args.addAll(ids);
        return jdbc.query("SELECT id,sent_at,direction,text_content FROM wa_message WHERE tenant_id=? AND id IN ("+placeholders+") AND deleted=0 ORDER BY sent_at,id",
                (rs,row)->new EvidenceView(rs.getLong("id"),local(rs,"sent_at"),rs.getString("direction"),excerpt(sanitize(rs.getString("text_content")))),args.toArray());
    }

    @Transactional
    public void decide(long recommendationId, RecommendationDecision request, Long userId) {
        long tenant=requiredTenant();
        int count=jdbc.update("""
                UPDATE wa_followup_recommendation SET status=?,handled_by=?,handled_at=NOW(3),handle_note=?
                WHERE tenant_id=? AND id=? AND status IN ('PENDING','ADOPTED') AND deleted=0
                """,request.status(),userId,request.note(),tenant,recommendationId);
        if(count!=1)throw new IllegalStateException("推荐不存在或已处理");
    }

    private AnalysisJobContext context(long tenant,long jobId,long keyId) {
        JobRow job=jdbc.queryForObject("""
                SELECT id,tenant_id,customer_id,wa_contact_id,attempt_count,max_attempts,HEX(context_version_hash) version
                FROM wa_analysis_job WHERE tenant_id=? AND id=? AND status='CLAIMED' AND claimed_by_agent_key_id=?
                """,(rs,row)->new JobRow(rs.getLong("id"),rs.getLong("tenant_id"),rs.getLong("customer_id"),
                rs.getLong("wa_contact_id"),rs.getInt("attempt_count"),rs.getInt("max_attempts"),rs.getString("version")),tenant,jobId,keyId);
        List<ContextMessage> messages=contextMessages(tenant,job.contactId());
        jdbc.update("""
                UPDATE wa_analysis_job SET context_message_ids=?
                WHERE tenant_id=? AND id=? AND status='CLAIMED' AND claimed_by_agent_key_id=?
                """,json(messages.stream().map(ContextMessage::id).toList()),tenant,job.id(),keyId);
        LocalDateTime end=messages.isEmpty()?LocalDateTime.now():messages.get(messages.size()-1).sentAt();
        return new AnalysisJobContext(job.id(),job.version().toLowerCase(Locale.ROOT),alias(tenant,job.customerId()),
                end.minusDays(CONTEXT_DAYS),end,messages,orderFacts(tenant,job.customerId()));
    }

    private JobRow claimedJob(AgentPrincipal principal,long jobId) {
        List<JobRow> rows=jdbc.query("""
                SELECT id,tenant_id,customer_id,wa_contact_id,attempt_count,max_attempts,HEX(context_version_hash) version
                FROM wa_analysis_job WHERE tenant_id=? AND id=? AND status='CLAIMED' AND claimed_by_agent_key_id=?
                  AND lease_until>=NOW(3) AND deleted=0
                """,(rs,row)->new JobRow(rs.getLong("id"),rs.getLong("tenant_id"),rs.getLong("customer_id"),
                rs.getLong("wa_contact_id"),rs.getInt("attempt_count"),rs.getInt("max_attempts"),rs.getString("version")),
                principal.getTenantId(),jobId,principal.getKeyId());
        if(rows.size()!=1)throw new AccessDeniedException("分析任务租约无效");return rows.get(0);
    }

    private AnalysisResult completedResult(AgentPrincipal principal,long jobId){
        List<AnalysisResult> rows=jdbc.query("""
                SELECT a.id analysis_id,r.id recommendation_id
                FROM wa_analysis_job j JOIN wa_customer_analysis a ON a.analysis_job_id=j.id AND a.tenant_id=j.tenant_id
                JOIN wa_followup_recommendation r ON r.analysis_id=a.id AND r.tenant_id=a.tenant_id
                WHERE j.tenant_id=? AND j.id=? AND j.status='SUCCEEDED' AND j.claimed_by_agent_key_id=?
                """,(rs,row)->new AnalysisResult(rs.getLong("analysis_id"),rs.getLong("recommendation_id"),"SUCCEEDED"),
                principal.getTenantId(),jobId,principal.getKeyId());
        return rows.isEmpty()?null:rows.get(0);
    }

    private List<ContextMessage> contextMessages(long tenant,long contactId) {
        List<ContextMessage> desc=jdbc.query("""
                SELECT m.id,m.sent_at,m.direction,m.text_content FROM wa_message m
                JOIN wa_conversation c ON c.id=m.conversation_id AND c.tenant_id=m.tenant_id
                WHERE m.tenant_id=? AND c.contact_id=? AND m.text_content IS NOT NULL AND m.text_content<>''
                  AND m.sent_at>=DATE_SUB(NOW(3),INTERVAL 90 DAY) AND m.deleted=0 AND m.source_deleted=0
                ORDER BY m.sent_at DESC,m.id DESC LIMIT 200
                """,(rs,row)->new ContextMessage(rs.getLong("id"),local(rs,"sent_at"),rs.getString("direction"),
                limit(sanitize(rs.getString("text_content")),MAX_MESSAGE_CHARS)),tenant,contactId);
        Collections.reverse(desc);return desc;
    }

    private List<Long> claimedContextMessageIds(JobRow job) {
        List<String> snapshots=jdbc.query("""
                SELECT context_message_ids FROM wa_analysis_job
                WHERE tenant_id=? AND id=? AND status='CLAIMED'
                """,(rs,row)->rs.getString(1),job.tenant(),job.id());
        if(snapshots.size()!=1||snapshots.get(0)==null)throw new IllegalStateException("分析任务缺少上下文快照");
        return longs(snapshots.get(0));
    }

    private OrderFacts orderFacts(long tenant,long customerId) {
        // 系列 E：统一经营订单口径（排除取消订单），不再让消费者自行决定状态范围
        String businessCondition="""
                AND (o.fulfillment_status IS NOT NULL AND o.fulfillment_status<>'CANCELLED'
                     OR (o.fulfillment_status IS NULL AND (o.status IS NULL OR o.status NOT IN (6,8))))
                """;
        Map<String,Object> aggregate=jdbc.queryForMap("""
                SELECT COUNT(*) order_count,MAX(create_time) last_order_at,COALESCE(SUM(total_amount),0) total_amount
                FROM sale_order o WHERE o.tenant_id=? AND o.customer_id=? AND o.deleted=0
                """+businessCondition,tenant,customerId);
        List<ProductFact> products=jdbc.query("""
                SELECT oi.product_name,oi.color_name,oi.size_name,SUM(oi.quantity) qty
                FROM sale_order_item oi JOIN sale_order o ON o.id=oi.order_id AND o.tenant_id=oi.tenant_id
                WHERE o.tenant_id=? AND o.customer_id=? AND o.deleted=0
                """+businessCondition+"""
                GROUP BY oi.product_name,oi.color_name,oi.size_name ORDER BY qty DESC LIMIT 20
                """,(rs,row)->new ProductFact(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4)),tenant,customerId);
        return new OrderFacts(((Number)aggregate.get("order_count")).longValue(),timestamp(aggregate.get("last_order_at")),
                amountRange((BigDecimal)aggregate.get("total_amount")),products);
    }

    private ContextStamp contextStamp(long tenant,long customerId,long contactId) {
        Map<String,Object> messages=jdbc.queryForMap("""
                SELECT COALESCE(MAX(m.id),0) last_id,MAX(m.sent_at) last_at,MAX(m.update_time) last_update,
                  COUNT(*) message_count
                FROM wa_message m JOIN wa_conversation c ON c.id=m.conversation_id AND c.tenant_id=m.tenant_id
                WHERE m.tenant_id=? AND c.contact_id=? AND m.text_content IS NOT NULL AND m.text_content<>''
                  AND m.sent_at>=DATE_SUB(NOW(3),INTERVAL 90 DAY) AND m.deleted=0 AND m.source_deleted=0
                """,tenant,contactId);
        Map<String,Object> orders=jdbc.queryForMap("""
                SELECT COUNT(*) order_count,MAX(update_time) last_update FROM sale_order o
                WHERE o.tenant_id=? AND o.customer_id=? AND o.deleted=0
                  AND (o.fulfillment_status IS NOT NULL AND o.fulfillment_status<>'CANCELLED'
                       OR (o.fulfillment_status IS NULL AND (o.status IS NULL OR o.status NOT IN (6,8))))
                """,tenant,customerId);
        return new ContextStamp(((Number)messages.get("last_id")).longValue(),timestamp(messages.get("last_at")),timestamp(messages.get("last_update")),
                ((Number)messages.get("message_count")).longValue(),((Number)orders.get("order_count")).longValue(),timestamp(orders.get("last_update")));
    }

    private static String excerpt(String value){return limit(value,500);}
    private static String alias(long tenant,long customer){return "CUST-"+HexFormat.of().formatHex(sha256(tenant+":"+customer)).substring(0,12).toUpperCase(Locale.ROOT);}
    private static String amountRange(BigDecimal amount){if(amount==null||amount.signum()==0)return "NONE";if(amount.compareTo(new BigDecimal("1000"))<0)return "LT_1000";if(amount.compareTo(new BigDecimal("5000"))<0)return "1000_4999";if(amount.compareTo(new BigDecimal("10000"))<0)return "5000_9999";return "GE_10000";}
    private static String title(AnalysisCompleteRequest r){return ("HIGH".equals(r.churnRisk())?"高风险客户跟进":"客户跟进建议")+" · "+r.intentStage();}
    private static String randomToken(int bytes){byte[] raw=new byte[bytes];RANDOM.nextBytes(raw);return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);}
    private static byte[] sha256(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    private String json(Object value){try{return objectMapper.writeValueAsString(value==null?Map.of():value);}catch(Exception e){throw new IllegalArgumentException("JSON无法序列化",e);}}
    private Map<String,Object> map(String value){if(value==null)return Map.of();try{return objectMapper.readValue(value,new TypeReference<>(){});}catch(Exception e){return Map.of();}}
    private List<Long> longs(String value){if(value==null)return List.of();try{return objectMapper.readValue(value,new TypeReference<>(){});}catch(Exception e){return List.of();}}
    private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static LocalDateTime local(java.sql.ResultSet rs,String name)throws java.sql.SQLException{var t=rs.getTimestamp(name);return t==null?null:t.toLocalDateTime();}
    private static LocalDateTime timestamp(Object value){return value instanceof java.sql.Timestamp t?t.toLocalDateTime():null;}
    private long requiredTenant(){Long tenant=TenantContext.getTenantId();if(tenant==null)throw new AccessDeniedException("缺少租户上下文");return tenant;}
    private record ContextStamp(long lastMessageId,LocalDateTime lastMessageAt,LocalDateTime lastMessageUpdate,long messageCount,long orderCount,LocalDateTime lastOrderUpdate){}
    private record JobRow(long id,long tenant,long customerId,long contactId,int attempts,int maxAttempts,String version){}
}
