package com.pischule.mentionbot.util;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.response.BaseResponse;
import org.slf4j.spi.LoggingEventBuilder;

public final class LoggingUtil {
    private LoggingUtil() {}

    public static LoggingEventBuilder withMessage(LoggingEventBuilder builder, Message message) {
        if (message.chat() != null) {
            builder = builder.addKeyValue(CHAT_ID_KEY, message.chat().id());
        }
        if (message.from() != null) {
            builder = builder.addKeyValue(USER_ID_KEY, message.from().id());
        }
        return builder;
    }

    public static LoggingEventBuilder withResponse(LoggingEventBuilder builder, BaseResponse response) {
        if (response.isOk()) {
            return builder;
        }

        return builder.addKeyValue(ERROR_CODE_KEY, response.errorCode())
                .addKeyValue(ERROR_DESC_KEY, response.description());
    }

    public static final String CHAT_ID_KEY = "chat_id";
    private static final String USER_ID_KEY = "user_id";
    private static final String ERROR_DESC_KEY = "error_description";
    private static final String ERROR_CODE_KEY = "error_code";
}
