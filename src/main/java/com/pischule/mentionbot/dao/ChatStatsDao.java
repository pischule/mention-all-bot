package com.pischule.mentionbot.dao;

import com.pischule.mentionbot.model.ChatStats;
import com.pischule.mentionbot.util.JdbcTemplate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class ChatStatsDao {
    private final JdbcTemplate jdbcTemplate;

    public ChatStatsDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void update(long chatId) {
        jdbcTemplate.update("""
                insert into chat_stats (chat_id, last_active_at)
                values (?, ?)
                on conflict do update set last_active_at = excluded.last_active_at
                """, chatId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public ChatStats getRecentStats() {
        var sql = """
                select coalesce(sum(users_count), 0)                                                 as users,
                       count(*)                                                                      as chats,
                       coalesce(sum(case when users_count > 1 then 1 else 0 end), 0)                 as groups,
                       coalesce(sum(case when users_count = 0 then 1 else 0 end), 0)                 as b0,
                       coalesce(sum(case when users_count = 1 then 1 else 0 end), 0)                 as b1,
                       coalesce(sum(case when users_count between 2 and 5 then 1 else 0 end), 0)     as b5,
                       coalesce(sum(case when users_count between 6 and 10 then 1 else 0 end), 0)    as b10,
                       coalesce(sum(case when users_count between 11 and 25 then 1 else 0 end), 0)   as b25,
                       coalesce(sum(case when users_count between 26 and 50 then 1 else 0 end), 0)   as b50,
                       coalesce(sum(case when users_count between 51 and 100 then 1 else 0 end), 0)  as b100,
                       coalesce(sum(case when users_count between 101 and 250 then 1 else 0 end), 0) as b250,
                       coalesce(sum(case when users_count between 251 and 500 then 1 else 0 end), 0) as b500,
                       coalesce(sum(case when users_count > 500 then 1 else 0 end), 0)               as bmore
                from chat_stats
                """;
        return jdbcTemplate
                .query(
                        sql,
                        rs -> new ChatStats(
                                rs.getInt("users"),
                                rs.getInt("chats"),
                                rs.getInt("groups"),
                                rs.getInt("b0"),
                                rs.getInt("b1"),
                                rs.getInt("b5"),
                                rs.getInt("b10"),
                                rs.getInt("b25"),
                                rs.getInt("b50"),
                                rs.getInt("b100"),
                                rs.getInt("b250"),
                                rs.getInt("b500"),
                                rs.getInt("bmore")))
                .getFirst();
    }
}
