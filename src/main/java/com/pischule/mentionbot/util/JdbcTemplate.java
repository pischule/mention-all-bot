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

    public int update(String sql, Object... params) {
        try (var conn = getConnection();
                var pst = conn.prepareStatement(sql)) {

            pst.setQueryTimeout(statementTimeoutSeconds);
            setParameters(pst, params);
            return pst.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Database update failed", e);
        }
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... params) {
        try (var conn = getConnection();
                var pst = conn.prepareStatement(sql)) {
            pst.setQueryTimeout(statementTimeoutSeconds);
            setParameters(pst, params);

            var results = new ArrayList<T>();
            try (var rs = pst.executeQuery()) {
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs));
                }
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("Database query failed", e);
        }
    }

    private void setParameters(PreparedStatement pst, Object... params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                pst.setObject(i + 1, params[i]);
            }
        }
    }
}
