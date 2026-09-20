package com.pischule.mentionbot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.pischule.mentionbot.dao.SentMessageDao;
import com.pischule.mentionbot.util.LogKV;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageSender {
    private final Logger logger = LoggerFactory.getLogger(MessageSender.class);
    private static final Pattern RETRY_AFTER_PATTERN =
            Pattern.compile("^Too Many Requests: retry after (\\d+)$", Pattern.CASE_INSENSITIVE);

    private final TelegramBot bot;
    private final SentMessageDao sentMessageDao;
    private final ScheduledExecutorService executorService;
    private final ConcurrentHashMap<Long, ChatCtx> chatIdToCtx;
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

        this.chatIdToCtx = new ConcurrentHashMap<>();

        long windowMillis = RATE_LIMIT_WINDOW.toMillis();
        executorService.scheduleAtFixedRate(this::cleanup, windowMillis, windowMillis, TimeUnit.MILLISECONDS);
    }

    public void send(long chatId, String text) {
        send(chatId, List.of(text), null, false);
    }

    public void send(long chatId, List<String> texts, ParseMode parseMode, boolean deleteLater) {
        UUID traceId = LogKV.getTraceId();

        var messageContexts = texts.stream()
                .map(text -> {
                    var request = new SendMessage(chatId, text);
                    Optional.ofNullable(parseMode).ifPresent(request::setParseMode);
                    return new SendMessageContext(chatId, request, deleteLater, traceId);
                })
                .toList();

        var chatCtx = chatIdToCtx.computeIfAbsent(chatId, ChatCtx::new);
        chatCtx.pushLastAll(messageContexts);
        chatCtx.triggerIfIdle();
    }

    record SendMessageContext(long chatId, SendMessage request, boolean deleteLater, UUID traceId) {}

    private void sendInternal(long chatId) {
        ChatCtx chatCtx = chatIdToCtx.get(chatId);
        SendMessageContext ctx = chatCtx.pop();

        LogKV.withTrace(ctx.traceId(), () -> {
            int customDelayMs = -1;
            try {
                SendResponse response = rateLimiter.executeSupplier(() -> bot.execute(ctx.request()));
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
                    Integer rateLimitDelayMs = extractRateLimitedDelayMs(response);
                    if (rateLimitDelayMs != null) {
                        LogKV.withResponse(logger.atWarn(), response)
                                .log("Too many requests. Retrying in {} ms", rateLimitDelayMs);
                        chatCtx.pushFirst(ctx);
                        customDelayMs = rateLimitDelayMs;
                    } else {
                        LogKV.withResponse(logger.atError(), response).log("Failed to send message");
                    }
                }
            } catch (Exception e) {
                logger.atError().addKeyValue(LogKV.CHAT_ID, ctx.chatId()).log("Unexpected error while sending message");
            } finally {
                chatCtx.scheduleNext(customDelayMs);
            }
        });
    }

    class ChatCtx {
        private static final int SMALL_DELAY_MS = 1100;
        private static final int LARGE_DELAY_MS = 3100;

        private final ArrayDeque<SendMessageContext> queue = new ArrayDeque<>();
        int totalQueued = 0;
        private int regularDelayMs = SMALL_DELAY_MS;
        private boolean active = false;
        private Instant lastTouchedAt = Instant.now();
        private final long chatId;

        ChatCtx(long chatId) {
            this.chatId = chatId;
        }

        public synchronized void pushLastAll(List<SendMessageContext> sendMessages) {
            lastTouchedAt = Instant.now();
            queue.addAll(sendMessages);

            totalQueued += sendMessages.size();
            updateRegularDelay();
        }

        public synchronized void pushFirst(SendMessageContext sendMessage) {
            lastTouchedAt = Instant.now();
            queue.addFirst(sendMessage);

            totalQueued += 1;
            updateRegularDelay();
        }

        private void updateRegularDelay() {
            if (totalQueued >= 20) {
                regularDelayMs = LARGE_DELAY_MS;
            }
        }

        public synchronized SendMessageContext pop() {
            lastTouchedAt = Instant.now();
            return queue.pop();
        }

        // called from outside
        private synchronized void triggerIfIdle() {
            lastTouchedAt = Instant.now();
            if (!queue.isEmpty() && !active) {
                executorService.schedule(() -> sendInternal(chatId), 0, TimeUnit.MILLISECONDS);
                active = true;
            }
        }

        // called from finally scheduler block
        private synchronized void scheduleNext(int minDelayMs) {
            lastTouchedAt = Instant.now();
            if (queue.isEmpty()) {
                active = false;
                return;
            }

            int delayMs = Math.max(minDelayMs, regularDelayMs);
            executorService.schedule(() -> sendInternal(chatId), delayMs, TimeUnit.MILLISECONDS);
        }

        public Instant getLastTouchedAt() {
            return lastTouchedAt;
        }
    }

    private Integer extractRateLimitedDelayMs(SendResponse response) {
        if (response.errorCode() != 429) {
            return null;
        }

        String description = response.description();
        if (description == null) {
            return null;
        }

        Matcher matcher = RETRY_AFTER_PATTERN.matcher(description.trim());
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(1)) * 1_000;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    private void cleanup() {
        var now = Instant.now();

        // just in case
        var forgetAllBefore = now.minus(RATE_LIMIT_WINDOW).minus(RATE_LIMIT_WINDOW);

        int sizeBefore = chatIdToCtx.size();
        boolean removed = chatIdToCtx
                .entrySet()
                .removeIf(e -> e.getValue().getLastTouchedAt().isBefore(forgetAllBefore));
        int sizeAfter = chatIdToCtx.size();
        if (removed) {
            logger.debug("Cleaned chat contexts. {} -> {}", sizeBefore, sizeAfter);
        }
    }

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(70);
}
