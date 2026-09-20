package com.pischule.mentionbot.dao;

import com.pischule.mentionbot.model.SentMessage;
import com.pischule.mentionbot.util.JdbcTemplate;
import java.time.Instant;
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
                        Instant.ofEpochSecond(rs.getLong("created_at")),
                        rs.getLong("chat_id"),
                        rs.getInt("message_id")));
    }

    public void deleteById(long id) {
        var sql = """
                delete
                from sent_messages
                where id = ?
                """;
        jdbcTemplate.update(sql, id);
    }

    public void insert(long chatId, int messageId) {
        jdbcTemplate.update("""
                insert into sent_messages
                (chat_id, message_id, created_at)
                values (?, ?, ?)
                """, chatId, messageId, Instant.now().getEpochSecond());
    }
}
