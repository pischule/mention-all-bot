package com.pischule.mentionbot.util;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.response.BaseResponse;
import org.slf4j.spi.LoggingEventBuilder;

public final class LogKV {
    private LogKV() {}

    public static LoggingEventBuilder withMessage(LoggingEventBuilder builder, Message message) {
        if (message.chat() != null) {
            builder = builder.addKeyValue(CHAT_ID, message.chat().id());
        }
        if (message.from() != null) {
            builder = builder.addKeyValue(USER_ID, message.from().id());
        }
        return builder;
    }

    public static LoggingEventBuilder withResponse(LoggingEventBuilder builder, BaseResponse response) {
        if (response.isOk()) {
            return builder;
        }

        return builder.addKeyValue(ERROR_CODE, response.errorCode()).addKeyValue(ERROR_DESC, response.description());
    }

    public static final String CHAT_ID = "chat_id";
    public static final String MESSAGE_ID = "message_id";
    private static final String USER_ID = "user_id";
    private static final String ERROR_CODE = "error_code";
    private static final String ERROR_DESC = "error_description";
}
