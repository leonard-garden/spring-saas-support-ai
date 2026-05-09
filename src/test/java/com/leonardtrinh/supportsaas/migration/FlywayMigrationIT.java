package com.leonardtrinh.supportsaas.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    void allMigrationsAppliedSuccessfully() {
        var applied = flyway.info().applied();
        assertThat(applied).isNotEmpty();
        for (var info : applied) {
            assertThat(info.getState().isApplied())
                    .as("Migration %s should be applied", info.getVersion())
                    .isTrue();
            assertThat(info.getState().isFailed())
                    .as("Migration %s should not have failed", info.getVersion())
                    .isFalse();
        }
    }

    @Test
    void businessesTableHasRequiredColumns() {
        assertColumnsExist("businesses", List.of("id", "name", "slug", "plan_id", "stripe_customer_id", "suspended_at", "created_at"));
    }

    @Test
    void plansTableHasRequiredColumns() {
        assertColumnsExist("plans", List.of(
                "id", "name", "slug", "price_usd_monthly", "stripe_price_id",
                "max_knowledge_bases", "max_documents_per_kb", "max_messages_per_month",
                "max_members", "features", "is_active"
        ));
    }

    @Test
    void membersTableHasRequiredColumns() {
        assertColumnsExist("members", List.of(
                "id", "business_id", "email", "password_hash", "role", "email_verified", "created_at"
        ));
    }

    @Test
    void invitationsTableHasRequiredColumns() {
        assertColumnsExist("invitations", List.of(
                "id", "business_id", "email", "role", "token_hash", "expires_at", "accepted_at"
        ));
    }

    @Test
    void refreshTokensTableHasRequiredColumns() {
        assertColumnsExist("refresh_tokens", List.of(
                "id", "member_id", "token_hash", "expires_at", "revoked_at", "created_at"
        ));
    }

    @Test
    void auditLogsTableHasRequiredColumns() {
        assertColumnsExist("audit_logs", List.of(
                "id", "business_id", "member_id", "impersonator_id",
                "action", "resource_type", "resource_id", "metadata", "created_at"
        ));
    }

    @Test
    void plansSeedDataPresent() {
        List<Map<String, Object>> plans = jdbc.queryForList(
                "SELECT slug FROM plans ORDER BY price_usd_monthly");
        List<String> slugs = plans.stream()
                .map(r -> (String) r.get("slug"))
                .toList();
        assertThat(slugs).containsExactly("free", "starter", "pro", "business");
    }

    @Test
    void membersEmailIsGloballyUnique() {
        String constraint = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'members'
                  AND constraint_type = 'UNIQUE'
                  AND constraint_name = 'members_email_key'
                """, String.class);
        assertThat(constraint).isEqualTo("1");
    }

    @Test
    void pendingInvitationPartialIndexExists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'invitations'
                  AND indexname = 'uq_pending_invitation'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void requiredIndexesExist() {
        assertIndexExists("idx_members_business_id");
        assertIndexExists("idx_refresh_tokens_member");
        assertIndexExists("idx_audit_logs_business_created");
    }

    private void assertColumnsExist(String table, List<String> columns) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?", table);
        List<String> actual = rows.stream()
                .map(r -> (String) r.get("column_name"))
                .toList();
        assertThat(actual).as("Columns in table '%s'", table).containsAll(columns);
    }

    private void assertIndexExists(String indexName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?",
                Integer.class, indexName);
        assertThat(count).as("Index '%s' should exist", indexName).isEqualTo(1);
    }
}
