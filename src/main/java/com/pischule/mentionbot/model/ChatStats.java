package com.pischule.mentionbot.model;

public record ChatStats(
        int users,
        int chats,
        int groups,
        int b0,
        int b1,
        int b5,
        int b10,
        int b25,
        int b50,
        int b100,
        int b250,
        int b500,
        int bMore) {}
