package com.pischule.mentionbot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pischule.mentionbot.dao.ChatStatsDao;
import com.pischule.mentionbot.dao.ChatUserDao;
import com.pischule.mentionbot.dao.SentMessageDao;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelegramUpdateHandler {
    private static final Logger logger = LoggerFactory.getLogger(TelegramUpdateHandler.class);

    private final TelegramBot bot;

    private final ChatUserDao chatUserDao;
    private final SentMessageDao sentMessageDao;
    private final ChatStatsDao chatStatsDao;

    public TelegramUpdateHandler(
            TelegramBot bot, ChatUserDao chatUserDao, SentMessageDao sentMessageDao, ChatStatsDao chatStatsDao) {
        this.bot = bot;
        this.chatUserDao = chatUserDao;
        this.sentMessageDao = sentMessageDao;
        this.chatStatsDao = chatStatsDao;
    }

    public void startPolling() {
        bot.setUpdatesListener(
                updates -> {
                    for (var update : updates) {
                        try {
                            handle(update);
                        } catch (Exception e) {
                            logger.error("Exception while processing update {}", update, e);
                        }
                    }
                    return UpdatesListener.CONFIRMED_UPDATES_ALL;
                },
                e -> {
                    if (e.response() == null) {
                        logger.atError().log("Error while handling update", e);
                    } else {
                        var response = e.response();
                        logger.atError()
                                .addKeyValue("errorCode", response.errorCode())
                                .addKeyValue("description", response.description())
                                .log("Error error from telegram", e);
                    }
                });
        logger.atInfo().log("Subscribed to telegram updates");
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

        logger.atInfo().log("Got command {}", command);
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
        send(message.chat().id(), text, ParseMode.MarkdownV2);
    }

    private void handleStats(Message message) {
        long chatId = message.chat().id();

        var stats = chatUserDao.getStats();
        var text = """
                `Users:  %6d
                Chats:  %6d
                Groups: %6d`
                """.formatted(stats.users(), stats.chats(), stats.groups());

        send(chatId, text, ParseMode.MarkdownV2);
    }

    private void handleNewChatMembers(Message message) {
        long chatId = message.chat().id();
        for (var user : message.newChatMembers()) {
            long userId = user.id();

            logger.atInfo()
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

        logger.atInfo()
                .addKeyValue("chat_id", chatId)
                .addKeyValue("user_id", userId)
                .log("User left chat");

        chatUserDao.delete(chatId, userId);
    }

    private void handleAll(Message message) {
        long chatId = message.chat().id();
        var users = chatUserDao.findAllByChatId(chatId);
        if (users.isEmpty()) {
            send(chatId, "There are no users. To opt in type /in command", null);
            return;
        }

        touchChatStats(message);

        var mentions = users.stream()
                .map(chatUser -> {
                    var escapedUsername = StringEscapeUtils.escapeHtml4(chatUser.username());
                    return "<a href=\"tg://user?id=%s\">%s</a>".formatted(chatUser.userId(), escapedUsername);
                })
                .toList();

        for (int offset = 0; offset < mentions.size(); offset += MENTIONS_PER_MESSAGE) {
            var toIndex = Math.min(offset + MENTIONS_PER_MESSAGE, mentions.size());
            var chunk = mentions.subList(offset, toIndex);

            var text = String.join(" ", chunk);
            var sendMessage = new SendMessage(chatId, text);
            sendMessage.setParseMode(ParseMode.HTML);

            var response = bot.execute(sendMessage);
            if (!response.isOk()) {
                logger.error("Failed to send message {}", response);
            }

            sentMessageDao.insert(chatId, response.message().messageId());
        }
    }

    private void handleOut(Message message) {
        long chatId = message.chat().id();
        long userId = message.from().id();
        chatUserDao.delete(chatId, userId);

        touchChatStats(message);

        var username = extractUsername(message.from());
        send(chatId, "You've been opted out %s".formatted(username), null);
    }

    private void handleStart(Message message) {
        long chatId = message.chat().id();
        String text = "Hey! I can help notify everyone 📢 in the group when someone needs them. "
                + "Everyone who wishes to receive mentions needs to /in to opt-in. "
                + "All opted-in users can then be mentioned using /all";
        send(chatId, text, null);
    }

    private void handleIn(Message message) {
        User from = message.from();
        if (from == null) return;

        touchChatStats(message);

        long chatId = message.chat().id();
        long userId = from.id();
        var username = extractUsername(from);

        chatUserDao.insert(chatId, userId, username);

        send(chatId, "Thanks for opting in %s".formatted(username), null);
    }

    private void send(long chatId, String text, ParseMode parseMode) {
        var request = new SendMessage(chatId, text);
        if (parseMode != null) {
            request.setParseMode(parseMode);
        }
        var response = bot.execute(request);
        if (!response.isOk()) {
            logger.error("Failed to send message. {}", response);
        }
    }

    private String extractUsername(User user) {
        return Stream.of(user.username(), user.firstName())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("anonymous");
    }

    private void touchChatStats(Message message) {
        var chatId = message.chat().id();
        chatStatsDao.update(chatId);
    }

    private static final int MENTIONS_PER_MESSAGE = 4;
}
