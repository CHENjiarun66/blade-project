package com.blade.whatsapp.auth;

import com.blade.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CollectorAuthenticationService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public CollectorPrincipal authenticate(String rawKey, String ip) {
        if (rawKey == null || rawKey.isBlank()) throw new BadCredentialsException("Missing collector key");
        int separator = rawKey.indexOf('.');
        if (separator <= 0 || separator == rawKey.length() - 1) throw new BadCredentialsException("Invalid collector key");
        String prefix = rawKey.substring(0, separator);
        String secret = rawKey.substring(separator + 1);
        List<KeyRow> rows = jdbcTemplate.query("""
                SELECT id, tenant_id, account_id, key_hash, scopes, expires_time
                FROM wa_collector_key WHERE key_prefix=? AND status=1 AND deleted=0
                """, (rs, row) -> new KeyRow(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getLong("account_id"), rs.getString("key_hash"), rs.getString("scopes"),
                rs.getTimestamp("expires_time") == null ? null : rs.getTimestamp("expires_time").toLocalDateTime()), prefix);
        if (rows.size() != 1 || (rows.get(0).expiresAt() != null && rows.get(0).expiresAt().isBefore(LocalDateTime.now()))
                || !passwordEncoder.matches(secret, rows.get(0).hash())) {
            TenantContext.clear();
            throw new BadCredentialsException("Invalid collector key");
        }
        KeyRow row = rows.get(0);
        TenantContext.setTenantId(row.tenantId());
        jdbcTemplate.update("UPDATE wa_collector_key SET last_used_time=NOW(3), last_used_ip=? WHERE id=? AND tenant_id=?",
                sanitizeIp(ip), row.id(), row.tenantId());
        return CollectorPrincipal.of(row.id(), row.tenantId(), row.accountId(), row.scopes());
    }

    private String sanitizeIp(String value) {
        if (value == null) return null;
        String first = value.split(",")[0].trim();
        return first.length() > 64 ? first.substring(0, 64) : first;
    }

    private record KeyRow(Long id, Long tenantId, Long accountId, String hash, String scopes, LocalDateTime expiresAt) {}
}
