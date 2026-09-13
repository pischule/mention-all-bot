package com.pischule.mentionbot;

import com.pengrad.telegrambot.TelegramBot;
import com.pischule.mentionbot.dao.ChatStatsDao;
import com.pischule.mentionbot.dao.ChatUserDao;
import com.pischule.mentionbot.dao.SentMessageDao;
import com.pischule.mentionbot.dao.SqliteDao;
import com.pischule.mentionbot.service.MessageCleaner;
import com.pischule.mentionbot.service.TelegramUpdateHandler;
import com.pischule.mentionbot.util.JdbcTemplate;
import com.pischule.mentionbot.util.LiquibaseRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    static void main() throws Exception {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        var propertiesPath = Path.of("config/application.properties");
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(propertiesPath)) {
            properties.load(reader);
        }

        var liquibaseChangelogPath = "liquibase/changelog.sql";
        var jdbcUrl = properties.getProperty("jdbc-url");
        var botToken = properties.getProperty("bot-token");

        var liquibaseRunner = new LiquibaseRunner(jdbcUrl, liquibaseChangelogPath);
        liquibaseRunner.update();

        var jdbcTemplate = new JdbcTemplate(jdbcUrl, 30);

        var sqliteDao = new SqliteDao(jdbcTemplate);
        var chatUsersDao = new ChatUserDao(jdbcTemplate);
        var sentMessageDao = new SentMessageDao(jdbcTemplate);
        var chatStatsDao = new ChatStatsDao(jdbcTemplate);

        sqliteDao.enableJournalModeWal();

        var bot = new TelegramBot(botToken);
        var updateHandler = new TelegramUpdateHandler(bot, chatUsersDao, sentMessageDao, chatStatsDao);
        var messageCleaner = new MessageCleaner(sentMessageDao, bot);

        // cleanup
        var cleanerThread = new Thread(messageCleaner::launchLoop);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Received shutdown signal");

            cleanerThread.interrupt();
            logger.info("Stopped message cleaner");

            bot.shutdown();
            logger.info("Stopped bot");
        }));

        // start
        cleanerThread.start();
        updateHandler.startPolling();
    }
}
