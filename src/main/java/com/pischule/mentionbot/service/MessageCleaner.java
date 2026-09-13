package com.pischule.mentionbot.service;

import static com.pischule.mentionbot.util.LoggingUtil.CHAT_ID_KEY;
import static com.pischule.mentionbot.util.LoggingUtil.withResponse;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.DeleteMessages;
import com.pischule.mentionbot.dao.SentMessageDao;
import com.pischule.mentionbot.model.SentMessage;
import com.pischule.mentionbot.util.CollectionUtil;
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

    private void deleteOldMessages() throws InterruptedException {
        Instant deleteBefore = Instant.now().minus(Duration.ofHours(47));

        var messagesToDelete = sentMessageDao.findAll().stream()
                .filter(m -> m.createdAt().isBefore(deleteBefore))
                .toList();

        var chatIdToMessages = messagesToDelete.stream().collect(Collectors.groupingBy(SentMessage::chatId));

        for (var e : chatIdToMessages.entrySet()) {
            var chatId = e.getKey();
            var messages = e.getValue();

            var messageIds =
                    messages.stream().map(it -> Math.toIntExact(it.messageId())).toList();

            var messageIdChunks = CollectionUtil.chunked(messageIds, MESSAGES_PER_DELETE);

            for (var chunk : messageIdChunks) {
                var chunkArray = chunk.stream().mapToInt(it -> it).toArray();
                var response = bot.execute(new DeleteMessages(chatId, chunkArray));
                if (response.isOk()) {
                    logger.atInfo()
                            .addKeyValue(CHAT_ID_KEY, chatId)
                            .log("Deleted {} messages from chat", messages.size());
                } else {
                    withResponse(logger.atWarn(), response)
                            .addKeyValue(CHAT_ID_KEY, chatId)
                            .log("Failed to delete message");
                }

                Thread.sleep(Duration.ofSeconds(1));
            }

            for (var m : messages) {
                sentMessageDao.deleteById(m.id());
            }
        }
    }

    private static final int MESSAGES_PER_DELETE = 100;
}
