package com.pischule.mentionbot.util;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.response.BaseResponse;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
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

    public static void withTrace(@Nullable UUID traceId, Runnable action) {
        if (traceId == null) {
            traceId = UUID.randomUUID();
        }
        UUID traceIdConst = traceId;

        ScopedValue.where(LogKV.TRACE_ID, traceIdConst).run(() -> {
            try (var _ = MDC.putCloseable("trace_id", traceIdConst.toString().replaceAll("-", ""))) {
                action.run();
            }
        });
    }

    public static UUID getTraceId() {
        return TRACE_ID.orElse(UUID.randomUUID());
    }

    private static final ScopedValue<UUID> TRACE_ID = ScopedValue.newInstance();

    public static final String CHAT_ID = "chat_id";
    public static final String MESSAGE_ID = "message_id";
    private static final String USER_ID = "user_id";
    private static final String ERROR_CODE = "error_code";
    private static final String ERROR_DESC = "error_description";
}
