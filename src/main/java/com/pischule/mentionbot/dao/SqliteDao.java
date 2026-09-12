package com.pischule.mentionbot.dao;

import com.pischule.mentionbot.util.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqliteDao {
    private static final Logger logger = LoggerFactory.getLogger(SqliteDao.class);
    private final JdbcTemplate jdbcTemplate;

    public SqliteDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void enableJournalModeWal() {
        var journalMode = jdbcTemplate
                .query("pragma journal_mode=wal", rs -> rs.getString(1))
                .getFirst();
        logger.atInfo().log("Current journalMode is {}", journalMode);
    }
}
