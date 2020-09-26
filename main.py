from telegram.ext import Updater, CommandHandler, MessageHandler, Filters

import sqlite3

import telegram

from constants import *

import logging
logging.basicConfig(
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s', level=logging.INFO)

chats = dict()

def in_command(update, context):
    chat_id = update.effective_chat.id

    if not chat_id in chats:
        chats[chat_id] = set()

    user = update.effective_user
    user_name = user.username or user.first_name or 'anonymous'

    chats[chat_id].add((user.id, user_name))
    message = f'Thanks for opting in {user_name}'

    context.bot.send_message(chat_id=chat_id, text=message)


def out_command(update, context):
    chat_id = update.effective_chat.id

    if chat_id not in chats:
        chats[chat_id] = set()

    user = update.effective_user
    user_name = user.username or user.first_name or 'anonymous'

    if user in chats[chat_id]: chats[chat_id].remove(user)

    message = f'You\' ve been opted out {user_name}'

    context.bot.send_message(chat_id=chat_id, text=message)


def all_command(update, context):
    chat_id = update.effective_chat.id

    if (chat_id not in chats) or (not chats[chat_id]):
        chats[chat_id] = set()
        message = 'There are no users\\. To opt in type /in command'
    else:
        user_list = [f'[{user_name}](tg://user?id={user_id})' for user_id, user_name in chats[chat_id]]
        message = ', '.join(user_list)

    context.bot.send_message(chat_id=chat_id, text=message, parse_mode=telegram.ParseMode.MARKDOWN_V2)


def test(update, context):
    chat_id = update.effective_chat.id
    user_id = update.effective_user.id
    username = update.effective_user.username

    message = f'[{username}](tg://user?id={user_id})'

    context.bot.send_message(chat_id=chat_id, text=message,
                             parse_mode=telegram.ParseMode.MARKDOWN_V2)

def unknown_command(update, context):
    context.bot.send_message(chat_id=update.effective_chat.id, text="Sorry, I didn't understand that command.")


updater = Updater(token=TOKEN, use_context=True)
dispatcher = updater.dispatcher


in_handler = CommandHandler('in', in_command)
all_handler = CommandHandler('all', all_command)
out_handler = CommandHandler('out', out_command)
test_handler = CommandHandler('test', test)
unknown_command = MessageHandler(Filters.command, unknown_command)

dispatcher.add_handler(in_handler)
dispatcher.add_handler(all_handler)
dispatcher.add_handler(out_handler)
dispatcher.add_handler(test_handler)
dispatcher.add_handler(unknown_command)


updater.start_polling()
updater.idle()
