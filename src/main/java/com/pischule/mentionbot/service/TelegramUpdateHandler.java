package com.pischule.mentionbot.service;

import static com.pischule.mentionbot.util.CollectionUtil.chunked;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.TelegramException;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pischule.mentionbot.dao.ChatStatsDao;
import com.pischule.mentionbot.dao.ChatUserDao;
import com.pischule.mentionbot.util.LogKV;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelegramUpdateHandler {
    private final Logger logger = LoggerFactory.getLogger(TelegramUpdateHandler.class);

    private final TelegramBot bot;

    private final ChatUserDao chatUserDao;
    private final ChatStatsDao chatStatsDao;
    private final MessageSender messageSender;

    public TelegramUpdateHandler(
            TelegramBot bot, ChatUserDao chatUserDao, ChatStatsDao chatStatsDao, MessageSender messageSender) {
        this.bot = bot;
        this.chatUserDao = chatUserDao;
        this.chatStatsDao = chatStatsDao;
        this.messageSender = messageSender;
    }

    public void startPolling() {
        bot.setUpdatesListener(
                updates -> {
                    for (var update : updates) {
                        try {
                            LogKV.withTrace(null, () -> handle(update));
                        } catch (Exception e) {
                            logger.error("Exception while processing update {}", update, e);
                        }
                    }
                    return UpdatesListener.CONFIRMED_UPDATES_ALL;
                },
                this::handleGetUpdatesError);
        logger.atInfo().log("Subscribed to telegram updates");
    }

    private void handleGetUpdatesError(TelegramException e) {
        var response = e.response();
        if (response == null) {
            logger.atError().log("Error while handling update", e);
            return;
        }

        if (response.errorCode() >= 500) {
            try {
                Thread.sleep(TG_5XX_SLEEP);
            } catch (InterruptedException ex) {
                logger.error("Updates error handler sleep interrupted", ex);
            }
        }

        logger.atError()
                .addKeyValue("errorCode", response.errorCode())
                .addKeyValue("description", response.description())
                .log("Error error from telegram", e);
    }

    public void handle(Update update) {
        Message message = update.message();
        if (message == null) {
            return;
        }

        if (message.leftChatMember() != null) {
            handleLeftChatMember(message);
            return;
        }

        if (message.newChatMembers() != null) {
            handleNewChatMembers(message);
        }

        var text = message.text();
        if (text == null) {
            return;
        }

        if (message.entities() == null) {
            return;
        }
        var commandEntity = Arrays.stream(message.entities())
                .filter(e -> e.type() == MessageEntity.Type.bot_command)
                .findFirst()
                .orElse(null);
        if (commandEntity == null) {
            return;
        }

        if (message.from() == null) {
            return;
        }
        var command = text.substring(commandEntity.offset(), commandEntity.offset() + commandEntity.length());
        switch (command) {
            case "/start" -> handleStart(message);
            case "/in" -> handleIn(message);
            case "/out" -> handleOut(message);
            case "/all" -> handleAll(message);
            case "/stats" -> handleStats(message);
            case "/stats_recent" -> handleStatsRecent(message);
        }
    }

    private void handleStatsRecent(Message message) {
        var stats = chatStatsDao.getRecentStats();
        var text = """
                `Users:   %5d
                Chats:   %5d
                Groups:  %5d

                Groups by size:
                0:       %5d
                1:       %5d
                2-5:     %5d
                6-10: 	  %5d
                11-25:   %5d
                26-50:   %5d
                51-100:  %5d
                101-250: %5d
                251-500: %5d
                500+: 	  %5d`
                """.formatted(
                        stats.users(),
                        stats.chats(),
                        stats.groups(),
                        stats.b0(),
                        stats.b1(),
                        stats.b5(),
                        stats.b10(),
                        stats.b25(),
                        stats.b50(),
                        stats.b100(),
                        stats.b250(),
                        stats.b50(),
                        stats.bMore());
        messageSender.send(message.chat().id(), List.of(text), ParseMode.MarkdownV2, false);
        LogKV.withMessage(logger.atInfo(), message).log("Processed STATS_RECENT");
    }

    private void handleStats(Message message) {
        long chatId = message.chat().id();

        var stats = chatUserDao.getStats();
        var text = """
                `Users:  %6d
                Chats:  %6d
                Groups: %6d`
                """.formatted(stats.users(), stats.chats(), stats.groups());

        messageSender.send(chatId, List.of(text), ParseMode.MarkdownV2, false);

        LogKV.withMessage(logger.atInfo(), message).log("Processed STATS");
    }

    private void handleNewChatMembers(Message message) {
        long chatId = message.chat().id();
        for (var user : message.newChatMembers()) {
            long userId = user.id();

            logger.atDebug()
                    .addKeyValue("chat_id", chatId)
                    .addKeyValue("user_id", user.id())
                    .log("User joined chat");

            String username = extractUsername(user);
            chatUserDao.insert(chatId, userId, username);
        }
    }

    private void handleLeftChatMember(Message message) {
        long chatId = message.chat().id();
        long userId = message.leftChatMember().id();

        chatUserDao.delete(chatId, userId);

        LogKV.withMessage(logger.atInfo(), message)
                .addKeyValue("user_id", userId)
                .log("Chat member left");
    }

    private void handleAll(Message message) {
        long chatId = message.chat().id();
        var users = chatUserDao.findAllByChatId(chatId);
        if (users.isEmpty()) {
            messageSender.send(chatId, "There are no users. To opt in type /in command");
            return;
        }

        chatStatsDao.updateLastActiveWithUsers(chatId, users.size());

        var allMentions = users.stream()
                .map(chatUser -> {
                    var escapedUsername = StringEscapeUtils.escapeHtml4(chatUser.username());
                    return "<a href=\"tg://user?id=%s\">%s</a>".formatted(chatUser.userId(), escapedUsername);
                })
                .toList();

        var mentionChunks = chunked(allMentions, MENTIONS_PER_MESSAGE);
        var messages = new ArrayList<String>();
        for (var chunk : mentionChunks) {
            var text = String.join(" ", chunk);
            messages.add(text);
        }
        messageSender.send(chatId, messages, ParseMode.HTML, true);

        LogKV.withMessage(logger.atInfo(), message).log("Processed ALL for {} users", users.size());
    }

    private void handleOut(Message message) {
        long chatId = message.chat().id();
        long userId = message.from().id();
        chatUserDao.delete(chatId, userId);

        chatStatsDao.updateLastActive(chatId);

        var username = extractUsername(message.from());
        messageSender.send(chatId, "You've been opted out %s".formatted(username));

        LogKV.withMessage(logger.atInfo(), message).log("Processed OUT command");
    }

    private void handleStart(Message message) {
        long chatId = message.chat().id();
        String text = "Hey! I can help notify everyone 📢 in the group when someone needs them. "
                + "Everyone who wishes to receive mentions needs to /in to opt-in. "
                + "All opted-in users can then be mentioned using /all";
        messageSender.send(chatId, text);
        LogKV.withMessage(logger.atInfo(), message).log("Processed START");
    }

    private void handleIn(Message message) {
        User from = message.from();
        if (from == null) return;

        long chatId = message.chat().id();
        long userId = from.id();
        var username = extractUsername(from);

        chatStatsDao.updateLastActive(chatId);

        chatUserDao.insert(chatId, userId, username);
        messageSender.send(chatId, "Thanks for opting in %s".formatted(username));
        LogKV.withMessage(logger.atInfo(), message).log("Processed IN");
    }

    private String extractUsername(User user) {
        return Stream.of(user.username(), user.firstName())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("anonymous");
    }

    private static final int MENTIONS_PER_MESSAGE = 4;
    private static final Duration TG_5XX_SLEEP = Duration.ofSeconds(5);
}
