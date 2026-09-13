package com.pischule.mentionbot.dao;

import com.pischule.mentionbot.model.SentMessage;
import com.pischule.mentionbot.util.JdbcTemplate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public class SentMessageDao {
    private final JdbcTemplate jdbcTemplate;

    public SentMessageDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SentMessage> findAll() {
        var sql = """
                select id, created_at, chat_id, message_id
                from sent_messages
                """;
        return jdbcTemplate.query(
                sql,
                rs -> new SentMessage(
                        rs.getLong("id"),
                        // for backward compatibility with Go datetime format
                        OffsetDateTime.parse(rs.getString("created_at").replace(" ", "T"))
                                .toInstant(),
                        rs.getLong("chat_id"),
                        rs.getLong("message_id")));
    }

    public void deleteById(long id) {
        var sql = """
                delete
                from sent_messages
                where id = ?
                """;
        jdbcTemplate.update(sql, id);
    }

    public void insert(long chatId, long messageId) {
        jdbcTemplate.update("""
                insert into sent_messages
                (chat_id, message_id, created_at)
                values (?, ?, ?)
                """, chatId, messageId, Instant.now().toString());
    }
}
