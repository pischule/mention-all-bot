package com.pischule.mentionbot.dao;

import com.pischule.mentionbot.model.ChatUser;
import com.pischule.mentionbot.model.ChatUserStats;
import com.pischule.mentionbot.util.JdbcTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ChatUserDao {
    private final JdbcTemplate jdbcTemplate;

    public ChatUserDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(long chatId, long userId, String username) {
        jdbcTemplate.update("""
                insert into chat_users (chat_id, user_id, username)
                values (?, ?, ?)
                on conflict do update set username = excluded.username
                """, chatId, userId, username);
    }

    public void delete(long chatId, long userId) {
        jdbcTemplate.update("""
                delete
                from chat_users
                where chat_id = ?
                  and user_id = ?
                """, chatId, userId);
    }

    public List<ChatUser> findAllByChatId(long chatId) {
        return jdbcTemplate.query("""
                select chat_id, user_id, username
                from chat_users
                where chat_id = ?
                """, this::mapRow, chatId);
    }

    public ChatUserStats getStats() {
        return jdbcTemplate
                .query("""
                        with groups as (select chat_id, count(1)
                                        from chat_users
                                        group by chat_id
                                        having count(1) > 1)
                        select (select count(distinct user_id) from chat_users) as users,
                               (select count(distinct chat_id) from chat_users) as chats,
                               (select count(distinct chat_id) from groups)     as groups
                        """, (rs -> new ChatUserStats(rs.getInt("users"), rs.getInt("chats"), rs.getInt("groups"))))
                .getFirst();
    }

    private ChatUser mapRow(ResultSet rs) throws SQLException {
        return new ChatUser(rs.getLong("chat_id"), rs.getLong("user_id"), rs.getString("username"));
    }
}
