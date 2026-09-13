-- liquibase formatted sql

-- changeset pischule:1
create table if not exists chat_users
(
    chat_id  integer not null,
    user_id  integer not null,
    username text,
    primary key (chat_id, user_id)
);

create table if not exists sent_messages
(
    id         integer primary key,
    created_at text,
    updated_at text,
    deleted_at text,
    message_id integer,
    chat_id    integer
);

create index if not exists idx_sent_messages_deleted_at on sent_messages (deleted_at);

create table if not exists chat_stats
(
    chat_id        integer primary key,
    last_active_at text,
    users_count    integer
);

-- changeset pischule:2
drop index if exists idx_sent_messages_deleted_at;

alter table sent_messages
    drop column updated_at;

alter table sent_messages
    drop column deleted_at;
