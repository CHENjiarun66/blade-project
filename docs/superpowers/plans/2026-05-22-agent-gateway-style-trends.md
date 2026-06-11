# Agent Gateway Style Trends Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first read-only Agent Gateway slice with tenant-bound Agent authentication and a style trend data package.

**Architecture:** Keep Agent access behind `/api/agent/**` instead of exposing business tables or reusing user JWTs as the integration contract. A dedicated Agent key authenticates the caller, sets `TenantContext`, exposes only scoped read endpoints, and reuses the existing analytics aggregation service for style trend facts.

**Tech Stack:** Spring Boot 3, Spring Security, MyBatis-Plus, Flyway, JUnit 5

---

## File Map

- Create `blade-backend/src/main/java/com/blade/agent/**` for Agent key entities, mapper, authentication filter/service, trend DTO/service, and controller.
- Modify `blade-backend/src/main/java/com/blade/config/SecurityConfig.java` to insert Agent authentication before JWT auth and remove unauthenticated customer access.
- Create `blade-backend/src/main/resources/db/migration/V33__agent_gateway_keys.sql` for tenant-bound hashed Agent keys and audit metadata.
- Create focused tests under `blade-backend/src/test/java/com/blade/agent/**` for trend packaging and authentication behavior.
- Update `docs/03-TASKS.md` and `docs/05-CHANGELOG.md` after verification.

### Task 1: Trend Package Contract

**Files:**
- Create: `blade-backend/src/test/java/com/blade/agent/AgentStyleTrendServiceTest.java`
- Create: `blade-backend/src/main/java/com/blade/agent/dto/AgentStyleTrendDTO.java`
- Create: `blade-backend/src/main/java/com/blade/agent/service/AgentStyleTrendService.java`

- [ ] **Step 1: Write the failing service test**

```java
@Test
void getStyleTrends_returnsProductRankingWithoutProfitFields() {
    when(analyticsService.getProductRanking(query, AnalyticsDimension.PRODUCT, AnalyticsSortBy.SALES, 20))
            .thenReturn(List.of(ranking("624-1#", "120.00", 16L)));

    AgentStyleTrendDTO result = service.getStyleTrends(query, 20);

    assertEquals("PRODUCT", result.getDimension());
    assertEquals("624-1#", result.getRows().get(0).getProductName());
    assertNull(result.getRows().get(0).getGrossProfit());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=AgentStyleTrendServiceTest test` from `blade-backend`

Expected: FAIL because `AgentStyleTrendService` and `AgentStyleTrendDTO` do not exist yet.

- [ ] **Step 3: Implement the smallest style trend package**

```java
public AgentStyleTrendDTO getStyleTrends(DashboardQueryDTO query, Integer limit) {
    List<AnalyticsRankingDTO> rows = analyticsService.getProductRanking(
            query, AnalyticsDimension.PRODUCT, AnalyticsSortBy.SALES, limit);
    return AgentStyleTrendDTO.productSales(rows);
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=AgentStyleTrendServiceTest test` from `blade-backend`

Expected: PASS.

### Task 2: Agent Key Authentication Boundary

**Files:**
- Create: `blade-backend/src/test/java/com/blade/agent/AgentKeyAuthenticationServiceTest.java`
- Create: `blade-backend/src/main/java/com/blade/agent/auth/AgentScope.java`
- Create: `blade-backend/src/main/java/com/blade/agent/auth/AgentPrincipal.java`
- Create: `blade-backend/src/main/java/com/blade/agent/auth/AgentKeyAuthenticationService.java`
- Create: `blade-backend/src/main/java/com/blade/agent/entity/AgentKey.java`
- Create: `blade-backend/src/main/java/com/blade/agent/mapper/AgentKeyMapper.java`
- Create: `blade-backend/src/main/resources/db/migration/V33__agent_gateway_keys.sql`

- [ ] **Step 1: Write the failing authentication test**

```java
@Test
void authenticate_setsTenantAndAuthoritiesForActiveScopedKey() {
    when(agentKeyMapper.selectOne(any())).thenReturn(activeKey("agent_demo", 7L, "analytics:read"));

    AgentPrincipal principal = service.authenticate("agent_demo.secret");

    assertEquals(7L, TenantContext.getTenantId());
    assertEquals("agent_demo", principal.getKeyPrefix());
    assertTrue(principal.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("agent:analytics:read")));
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=AgentKeyAuthenticationServiceTest test` from `blade-backend`

Expected: FAIL because the Agent key authentication types do not exist yet.

- [ ] **Step 3: Implement minimal hashed-key lookup**

```java
public AgentPrincipal authenticate(String rawKey) {
    ParsedAgentKey parsed = ParsedAgentKey.parse(rawKey);
    AgentKey agentKey = agentKeyMapper.selectOne(new LambdaQueryWrapper<AgentKey>()
            .eq(AgentKey::getKeyPrefix, parsed.prefix())
            .eq(AgentKey::getStatus, AgentKey.STATUS_ACTIVE));
    if (agentKey == null || !passwordEncoder.matches(parsed.secret(), agentKey.getKeyHash())) {
        throw new BadCredentialsException("Invalid agent key");
    }
    TenantContext.setTenantId(agentKey.getTenantId());
    return AgentPrincipal.from(agentKey);
}
```

- [ ] **Step 4: Run the authentication test**

Run: `mvn -q -Dtest=AgentKeyAuthenticationServiceTest test` from `blade-backend`

Expected: PASS.

### Task 3: `/api/agent` HTTP Access

**Files:**
- Create: `blade-backend/src/test/java/com/blade/agent/AgentAuthenticationFilterTest.java`
- Create: `blade-backend/src/main/java/com/blade/agent/auth/AgentAuthenticationFilter.java`
- Create: `blade-backend/src/main/java/com/blade/agent/controller/AgentAnalyticsController.java`
- Modify: `blade-backend/src/main/java/com/blade/config/SecurityConfig.java`

- [ ] **Step 1: Write failing filter and route tests**

```java
@Test
void filterAuthenticatesAgentRequestsFromAgentKeyHeader() throws Exception {
    when(authenticationService.authenticate("agent_demo.secret")).thenReturn(principal());

    filter.doFilter(request("/api/agent/analytics/style-trends", "agent_demo.secret"), response, chain);

    assertEquals("agent_demo", SecurityContextHolder.getContext().getAuthentication().getName());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=AgentAuthenticationFilterTest test` from `blade-backend`

Expected: FAIL because `AgentAuthenticationFilter` is missing.

- [ ] **Step 3: Implement filter, controller, and security matcher**

```java
@GetMapping("/style-trends")
@PreAuthorize("hasAuthority('agent:analytics:read')")
public R<AgentStyleTrendDTO> getStyleTrends(@ModelAttribute DashboardQueryDTO query,
                                            @RequestParam(defaultValue = "20") Integer limit) {
    return R.ok(agentStyleTrendService.getStyleTrends(query, limit));
}
```

`SecurityConfig` must add the Agent filter before the JWT filter and must not leave `/api/customers/**` in `permitAll`.

- [ ] **Step 4: Run focused tests**

Run: `mvn -q -Dtest=AgentAuthenticationFilterTest,AgentStyleTrendServiceTest,AgentKeyAuthenticationServiceTest test` from `blade-backend`

Expected: PASS.

### Task 4: Documentation and Verification

**Files:**
- Modify: `docs/03-TASKS.md`
- Modify: `docs/05-CHANGELOG.md`

- [ ] **Step 1: Mark the completed Agent tasks and record the implementation**

Document the Agent Gateway first slice, customer security boundary correction, test coverage, and what remains for scheduled follow-up and WhatsApp integration.

- [ ] **Step 2: Verify code quality**

Run: `git diff --check`

Expected: no whitespace or patch format issues.

- [ ] **Step 3: Verify backend tests**

Run: `mvn -q -Dtest=AgentAuthenticationFilterTest,AgentStyleTrendServiceTest,AgentKeyAuthenticationServiceTest test` from `blade-backend`

Expected: PASS, or record any environment blocker in the final update.
