package com.pischule.mentionbot.model;

import java.time.Instant;

public record SentMessage(long id, Instant createdAt, long chatId, int messageId) {}
