package com.pischule.mentionbot.service;

import static com.pischule.mentionbot.util.LogKV.withResponse;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.DeleteMessages;
import com.pischule.mentionbot.dao.SentMessageDao;
import com.pischule.mentionbot.model.SentMessage;
import com.pischule.mentionbot.util.CollectionUtil;
import com.pischule.mentionbot.util.LogKV;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageCleaner {
    private final Logger logger = LoggerFactory.getLogger(MessageCleaner.class);
    private final SentMessageDao sentMessageDao;
    private final TelegramBot bot;

    public MessageCleaner(SentMessageDao sentMessageDao, TelegramBot bot) {
        this.sentMessageDao = sentMessageDao;
        this.bot = bot;
    }

    public void launchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(Duration.ofSeconds(60));
                deleteOldMessages();
            } catch (InterruptedException e) {
                logger.info("Message cleaner was interrupted. Shutting down", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Got exception in message cleaner thread", e);
            }
        }
    }

    private void deleteOldMessages() throws InterruptedException {
        Instant deleteBefore = Instant.now().minus(Duration.ofHours(DELETE_MESSAGES_AFTER_HOURS));

        var messagesToDelete = sentMessageDao.findAll().stream()
                .filter(m -> m.createdAt().isBefore(deleteBefore))
                .toList();

        var chatIdToMessages = messagesToDelete.stream().collect(Collectors.groupingBy(SentMessage::chatId));

        for (var e : chatIdToMessages.entrySet()) {
            var chatId = e.getKey();
            var messages = e.getValue();

            var messageIds = messages.stream().map(SentMessage::messageId).toList();

            var messageIdChunks = CollectionUtil.chunked(messageIds, MESSAGES_PER_DELETE);

            for (var chunk : messageIdChunks) {
                var chunkArray = chunk.stream().mapToInt(it -> it).toArray();
                var response = bot.execute(new DeleteMessages(chatId, chunkArray));
                if (response.isOk()) {
                    logger.atInfo()
                            .addKeyValue(LogKV.CHAT_ID, chatId)
                            .log("Deleted {} messages from chat", chunk.size());
                } else {
                    withResponse(logger.atWarn(), response)
                            .addKeyValue(LogKV.CHAT_ID, chatId)
                            .log("Failed to delete message");
                }

                Thread.sleep(Duration.ofSeconds(2));
            }

            for (var m : messages) {
                sentMessageDao.deleteById(m.id());
            }
        }
    }

    private static final int DELETE_MESSAGES_AFTER_HOURS = 24;
    private static final int MESSAGES_PER_DELETE = 100;
}
