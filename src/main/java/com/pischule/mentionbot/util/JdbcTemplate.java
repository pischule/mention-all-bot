package com.pischule.mentionbot.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcTemplate {
    private final String jdbcUrl;
    private final int statementTimeoutSeconds;

    public JdbcTemplate(String jdbcUrl, int statementTimeoutSeconds) {
        this.jdbcUrl = jdbcUrl;
        this.statementTimeoutSeconds = statementTimeoutSeconds;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    public void update(String sql, Object... params) {
        try (var conn = getConnection();
                var ps = conn.prepareStatement(sql)) {

            ps.setQueryTimeout(statementTimeoutSeconds);
            setParameters(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Database update failed", e);
        }
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... params) {
        try (var conn = getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(statementTimeoutSeconds);
            setParameters(ps, params);

            var results = new ArrayList<T>();
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs));
                }
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("Database query failed", e);
        }
    }

    private void setParameters(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
