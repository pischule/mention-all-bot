package com.pischule.mentionbot.service;

import static com.pischule.mentionbot.util.LoggingUtil.CHAT_ID_KEY;
import static com.pischule.mentionbot.util.LoggingUtil.withResponse;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pischule.mentionbot.dao.SentMessageDao;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageSender {
    private final Logger logger = LoggerFactory.getLogger(MessageSender.class);

    private final TelegramBot bot;
    private final SentMessageDao sentMessageDao;
    private final ScheduledExecutorService executorService;
    private final ConcurrentHashMap<Long, Instant> chatIdToLastSend;

    public MessageSender(TelegramBot bot, SentMessageDao sentMessageDao, ScheduledExecutorService executorService) {
        this.bot = bot;
        this.sentMessageDao = sentMessageDao;
        this.executorService = executorService;

        this.chatIdToLastSend = new ConcurrentHashMap<>();

        long windowMillis = RATE_LIMIT_WINDOW.toMillis();
        executorService.scheduleAtFixedRate(this::cleanup, windowMillis, windowMillis, TimeUnit.MILLISECONDS);
    }

    public void send(long chatId, String text) {
        send(chatId, text, null, false);
    }

    public void send(long chatId, String text, ParseMode parseMode, boolean deleteLater) {
        var request = new SendMessage(chatId, text);
        if (parseMode != null) {
            request.setParseMode(parseMode);
        }

        var ctx = new SendMessageContext(chatId, request, deleteLater);

        var now = Instant.now();
        var scheduleTime = chatIdToLastSend.compute(chatId, (_, old) -> {
            if (old == null) {
                return now;
            } else {
                return old.plus(SAME_CHAT_DELAY);
            }
        });

        var delayMillis = Duration.between(now, scheduleTime).toMillis();

        executorService.schedule(() -> sendInternal(ctx), delayMillis, TimeUnit.MILLISECONDS);

        cleanup();
    }

    record SendMessageContext(long chatId, SendMessage request, boolean deleteLater) {}

    private void sendInternal(SendMessageContext ctx) {
        var response = bot.execute(ctx.request());
        if (response.isOk()) {
            logger.atDebug().addKeyValue(CHAT_ID_KEY, ctx.chatId()).log("Sent message");
            if (ctx.deleteLater()) {
                sentMessageDao.insert(ctx.chatId(), response.message().messageId());
            }
        } else {
            withResponse(logger.atError(), response).log("Failed to send message");
        }
    }

    private void cleanup() {
        var now = Instant.now();

        var forgetAllBefore = now.minus(RATE_LIMIT_WINDOW);

        int sizeBefore = chatIdToLastSend.size();
        chatIdToLastSend.entrySet().removeIf(e -> e.getValue().isBefore(forgetAllBefore));
        int sizeAfter = chatIdToLastSend.size();

        logger.atInfo().log("Cleaned timer map. {} -> {}", sizeBefore, sizeAfter);
    }

    private static final Duration SAME_CHAT_DELAY = Duration.ofMillis(1100);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(70);
}
