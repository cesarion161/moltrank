package com.moltrank.clawgic.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClawgicJudgeResultSchemaMigrationFlywayTest {

    private static final String DB_URL = envOrDefault("C10_TEST_DB_URL", "jdbc:postgresql://localhost:5432/moltrank");
    private static final String DB_USERNAME = envOrDefault("C10_TEST_DB_USERNAME", "moltrank");
    private static final String DB_PASSWORD = envOrDefault("C10_TEST_DB_PASSWORD", "changeme");

    @Test
    void flywayFreshSchemaMigrationAddsMatchJudgementsTableAndConstraints() throws SQLException {
        String schemaName = randomSchemaName("c25_fresh");
        createSchema(schemaName);
        try {
            Flyway flyway = flywayForSchema(schemaName, null);
            flyway.migrate();

            assertTrue(tableExists(schemaName, "clawgic_match_judgements"));
            assertTrue(columnExists(schemaName, "clawgic_match_judgements", "judgement_id"));
            assertTrue(columnExists(schemaName, "clawgic_match_judgements", "match_id"));
            assertTrue(columnExists(schemaName, "clawgic_match_judgements", "judge_key"));
            assertTrue(columnExists(schemaName, "clawgic_match_judgements", "status"));
            assertTrue(columnExists(schemaName, "clawgic_match_judgements", "attempt"));
            assertTrue(columnUsesType(schemaName, "clawgic_match_judgements", "result_json", "jsonb"));
            assertTrue(columnUsesType(schemaName, "clawgic_match_judgements", "agent1_logic_score", "int4"));
            assertTrue(columnUsesType(schemaName, "clawgic_match_judgements", "agent2_logic_score", "int4"));

            assertTrue(constraintExists(schemaName, "uq_clawgic_match_judgements_match_judge_attempt"));
            assertTrue(constraintExists(schemaName, "chk_clawgic_match_judgements_status_valid"));
            assertTrue(constraintExists(schemaName, "chk_clawgic_match_judgements_result_json_object"));
            assertTrue(constraintExists(schemaName, "chk_clawgic_match_judgements_scores_all_or_none"));
            assertTrue(constraintExists(schemaName, "chk_clawgic_match_judgements_accepted_requires_scores"));

            assertTrue(indexExists(schemaName, "idx_clawgic_match_judgements_match_created_at"));
            assertTrue(indexExists(schemaName, "idx_clawgic_match_judgements_match_status_created_at"));
            assertTrue(indexExists(schemaName, "idx_clawgic_match_judgements_winner_agent_id"));
        } finally {
            dropSchema(schemaName);
        }
    }

    @Test
    void flywayUpgradeFromPreC25SchemaAppliesV13AndV14Cleanly() throws SQLException {
        String schemaName = randomSchemaName("c25_upgrade");
        createSchema(schemaName);
        try {
            Flyway preC25Flyway = flywayForSchema(schemaName, MigrationVersion.fromVersion("12"));
            preC25Flyway.migrate();

            assertTrue(tableExists(schemaName, "clawgic_matches"));
            assertEquals(0, countRows(schemaName, "flyway_schema_history", "version = '13'"));
            assertEquals(0, countRows(schemaName, "flyway_schema_history", "version = '14'"));

            Flyway latestFlyway = flywayForSchema(schemaName, null);
            latestFlyway.migrate();

            assertEquals(1, countRows(schemaName, "flyway_schema_history", "version = '13'"));
            assertEquals(1, countRows(schemaName, "flyway_schema_history", "version = '14'"));
            assertTrue(tableExists(schemaName, "clawgic_match_judgements"));
            assertTrue(columnExists(schemaName, "clawgic_match_judgements", "winner_agent_id"));
        } finally {
            dropSchema(schemaName);
        }
    }

    private static Flyway flywayForSchema(String schemaName, MigrationVersion targetVersion) {
        var configuration = Flyway.configure()
                .dataSource(DB_URL, DB_USERNAME, DB_PASSWORD)
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .baselineOnMigrate(true);
        if (targetVersion != null) {
            configuration.target(targetVersion);
        }
        return configuration.load();
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String randomSchemaName(String prefix) {
        return (prefix + "_" + UUID.randomUUID().toString().replace("-", ""))
                .toLowerCase(Locale.ROOT);
    }

    private static void createSchema(String schemaName) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schemaName);
        }
    }

    private static void dropSchema(String schemaName) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    private static boolean tableExists(String schemaName, String tableName) throws SQLException {
        return countRows(
                "information_schema.tables",
                "table_schema = ? AND table_name = ?",
                schemaName,
                tableName
        ) == 1;
    }

    private static boolean columnExists(String schemaName, String tableName, String columnName) throws SQLException {
        return countRows(
                "information_schema.columns",
                "table_schema = ? AND table_name = ? AND column_name = ?",
                schemaName,
                tableName,
                columnName
        ) == 1;
    }

    private static boolean columnUsesType(
            String schemaName,
            String tableName,
            String columnName,
            String udtName
    ) throws SQLException {
        return countRows(
                "information_schema.columns",
                "table_schema = ? AND table_name = ? AND column_name = ? AND udt_name = ?",
                schemaName,
                tableName,
                columnName,
                udtName
        ) == 1;
    }

    private static boolean indexExists(String schemaName, String indexName) throws SQLException {
        return countRows(
                "pg_indexes",
                "schemaname = ? AND indexname = ?",
                schemaName,
                indexName
        ) == 1;
    }

    private static boolean constraintExists(String schemaName, String constraintName) throws SQLException {
        return countRows(
                "pg_constraint c",
                "c.connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?) "
                        + "AND c.conname = ?",
                schemaName,
                constraintName
        ) == 1;
    }

    private static int countRows(String schemaName, String tableName, String whereClause) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + schemaName + "." + tableName + " WHERE " + whereClause;
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static int countRows(String table, String whereClause, String arg1, String arg2)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + whereClause;
        try (Connection connection = openConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, arg1);
            preparedStatement.setString(2, arg2);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static int countRows(String table, String whereClause, String arg1, String arg2, String arg3)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + whereClause;
        try (Connection connection = openConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, arg1);
            preparedStatement.setString(2, arg2);
            preparedStatement.setString(3, arg3);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static int countRows(
            String table,
            String whereClause,
            String arg1,
            String arg2,
            String arg3,
            String arg4
    ) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + whereClause;
        try (Connection connection = openConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, arg1);
            preparedStatement.setString(2, arg2);
            preparedStatement.setString(3, arg3);
            preparedStatement.setString(4, arg4);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
    }
}
