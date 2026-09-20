package com.pischule.mentionbot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pischule.mentionbot.dao.SentMessageDao;
import com.pischule.mentionbot.util.LogKV;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private final RateLimiter rateLimiter;

    public MessageSender(
            TelegramBot bot,
            SentMessageDao sentMessageDao,
            ScheduledExecutorService executorService,
            RateLimiter rateLimiter) {
        this.bot = bot;
        this.sentMessageDao = sentMessageDao;
        this.executorService = executorService;
        this.rateLimiter = rateLimiter;

        this.chatIdToLastSend = new ConcurrentHashMap<>();

        long windowMillis = RATE_LIMIT_WINDOW.toMillis();
        executorService.scheduleAtFixedRate(this::cleanup, windowMillis, windowMillis, TimeUnit.MILLISECONDS);
    }

    public void send(long chatId, String text) {
        send(chatId, List.of(text), null, false);
    }

    public void send(long chatId, List<String> texts, ParseMode parseMode, boolean deleteLater) {
        Duration delay;
        if (texts.size() >= 20) {
            delay = LARGE_CHAT_DELAY;
        } else {
            delay = SMALL_CHAT_DELAY;
        }

        var now = Instant.now();

        UUID traceId = LogKV.getTraceId();

        for (String text : texts) {
            var request = new SendMessage(chatId, text);
            Optional.ofNullable(parseMode).ifPresent(request::setParseMode);
            var ctx = new SendMessageContext(chatId, request, deleteLater, traceId);

            var scheduleTime = chatIdToLastSend.compute(chatId, (_, old) -> {
                if (old == null) {
                    return now;
                } else {
                    return old.plus(delay);
                }
            });

            var delayMillis = Duration.between(now, scheduleTime).toMillis();

            executorService.schedule(() -> sendInternal(ctx), delayMillis, TimeUnit.MILLISECONDS);
        }

        cleanup();
    }

    record SendMessageContext(long chatId, SendMessage request, boolean deleteLater, UUID traceId) {}

    private void sendInternal(SendMessageContext ctx) {
        LogKV.withTrace(ctx.traceId, () -> {
            var response = rateLimiter.executeSupplier(() -> bot.execute(ctx.request()));
            if (response.isOk()) {
                Integer messageId = response.message().messageId();
                logger.atInfo()
                        .addKeyValue(LogKV.CHAT_ID, ctx.chatId())
                        .addKeyValue(LogKV.MESSAGE_ID, messageId)
                        .log("Sent message");
                if (ctx.deleteLater()) {
                    sentMessageDao.insert(ctx.chatId(), messageId);
                }
            } else {
                LogKV.withResponse(logger.atError(), response).log("Failed to send message");
            }
        });
    }

    private void cleanup() {
        var now = Instant.now();

        var forgetAllBefore = now.minus(RATE_LIMIT_WINDOW);

        chatIdToLastSend.entrySet().removeIf(e -> e.getValue().isBefore(forgetAllBefore));
    }

    private static final Duration SMALL_CHAT_DELAY = Duration.ofMillis(1100);
    private static final Duration LARGE_CHAT_DELAY = Duration.ofMillis(3100);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(70);
}
