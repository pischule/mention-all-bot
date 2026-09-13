package com.pischule.mentionbot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.DeleteMessages;
import com.pischule.mentionbot.dao.SentMessageDao;
import com.pischule.mentionbot.model.SentMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageCleaner {
    private static final Logger logger = LoggerFactory.getLogger(MessageCleaner.class);
    private final SentMessageDao sentMessageDao;
    private final TelegramBot bot;

    public MessageCleaner(SentMessageDao sentMessageDao, TelegramBot bot) {
        this.sentMessageDao = sentMessageDao;
        this.bot = bot;
    }

    public void launchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                deleteOldMessages();
                Thread.sleep(Duration.ofSeconds(30));
            } catch (InterruptedException e) {
                logger.info("Message cleaner was interrupted. Shutting down", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Got exception in message cleaner thread", e);
            }
        }
    }

    private void deleteOldMessages() {
        Instant deleteBefore = Instant.now().minus(Duration.ofHours(47));

        var messagesToDelete = sentMessageDao.findAll().stream()
                .filter(m -> m.createdAt().isBefore(deleteBefore))
                .toList();

        var chatIdToMessages = messagesToDelete.stream().collect(Collectors.groupingBy(SentMessage::chatId));

        for (var e : chatIdToMessages.entrySet()) {
            var chatId = e.getKey();
            var messages = e.getValue();

            var messageIds = messages.stream()
                    .mapToInt(it -> Math.toIntExact(it.messageId()))
                    .toArray();
            var response = bot.execute(new DeleteMessages(chatId, messageIds));
            if (response.isOk()) {
                logger.atInfo().log("Deleted {} messages from chat {}", messages.size(), chatId);
            } else {
                logger.atWarn().log("Failed to delete message {}", response);
            }

            for (var m : messages) {
                sentMessageDao.deleteById(m.id());
            }
        }
    }
}
