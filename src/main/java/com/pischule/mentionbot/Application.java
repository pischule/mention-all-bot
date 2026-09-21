package com.pischule.mentionbot;

import com.pengrad.telegrambot.TelegramBot;
import com.pischule.mentionbot.dao.ChatStatsDao;
import com.pischule.mentionbot.dao.ChatUserDao;
import com.pischule.mentionbot.dao.SentMessageDao;
import com.pischule.mentionbot.service.MessageCleaner;
import com.pischule.mentionbot.service.MessageSender;
import com.pischule.mentionbot.service.TelegramUpdateHandler;
import com.pischule.mentionbot.util.JdbcTemplate;
import com.pischule.mentionbot.util.LiquibaseRunner;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

@NullMarked
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

        var chatUsersDao = new ChatUserDao(jdbcTemplate);
        var sentMessageDao = new SentMessageDao(jdbcTemplate);
        var chatStatsDao = new ChatStatsDao(jdbcTemplate);
        var senderExecutor = Executors.newScheduledThreadPool(4);

        var sendMessageRateLimiter = configureSendMessageRateLimiter();

        var bot = new TelegramBot(botToken);
        var messageSender = new MessageSender(bot, sentMessageDao, senderExecutor, sendMessageRateLimiter);
        var updateHandler = new TelegramUpdateHandler(bot, chatUsersDao, chatStatsDao, messageSender);
        var messageCleaner = new MessageCleaner(sentMessageDao, bot);

        // cleanup
        var cleanerThread = new Thread(messageCleaner::launchLoop, "clean");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.atInfo().log("Received shutdown signal");

            cleanerThread.interrupt();
            logger.atInfo().log("Stopped message cleaner");

            bot.shutdown();
            logger.atInfo().log("Stopped bot");

            logger.atInfo().log("Stopping sender executor");
            senderExecutor.shutdown();
            try {
                if (!senderExecutor.awaitTermination(90, TimeUnit.SECONDS)) {
                    senderExecutor.shutdownNow();
                    logger.warn("Force shutdown sender executor");
                } else {
                    logger.info("Gracefully shutdown sender executor");
                }
            } catch (InterruptedException e) {
                senderExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));

        // start
        cleanerThread.start();
        updateHandler.startPolling();
    }

    private static RateLimiter configureSendMessageRateLimiter() {
        var config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                // actual limit is 30
                .limitForPeriod(28)
                .timeoutDuration(Duration.ofSeconds(40))
                .build();
        var registry = RateLimiterRegistry.of(config);
        return registry.rateLimiter("sendMessage");
    }
}
