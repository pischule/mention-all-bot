import logging

import telegram
from telegram.ext import Updater, CommandHandler, MessageHandler, Filters
from telegram.utils.helpers import mention_markdown, escape_markdown

from bot_database import BotDatabase
from constants import *

logging.basicConfig(
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s', level=logging.INFO)

db = BotDatabase('database.db')


def start_command(update, context):
    text = 'Hey! I can help notify everyone in the group when someone needs them. \
Everyone who wishes to receive mentions needs to /in to opt-in. All opted-in users can then be mentioned using /all'
    context.bot.send_message(chat_id=update.effective_chat.id, text=text)


def in_command(update, context):
    chat_id = update.effective_chat.id
    user = update.effective_user
    user_name = user.username or user.first_name or 'anonymous'
    db.add_user(user.id, user_name)
    db.add_user_to_chat(chat_id, user.id)
    text = f'Thanks for opting in {user_name}'
    context.bot.send_message(chat_id=chat_id, text=text)


def out_command(update, context):
    chat_id = update.effective_chat.id
    user = update.effective_user
    user_name = user.username or user.first_name or 'anonymous'
    db.delete_user_from_chat(chat_id, user.id)
    message = f'You\' ve been opted out {user_name}'
    context.bot.send_message(chat_id=chat_id, text=message)


def all_command(update, context):
    chat_id = update.effective_chat.id
    user_list = db.get_users_from_chat(chat_id)
    message_id = update.message.message_id

    if not user_list:
        text = escape_markdown('There are no users. To opt in type /in command', version=2)
    else:
        user_list = [mention_markdown(user_id, '\u2060', version=2) for user_id, user_name in user_list]
        text = 'Mentioned *all*' + ''.join(user_list)
    context.bot.send_message(chat_id=chat_id, text=text, reply_to_message_id = message_id,
                             parse_mode=telegram.ParseMode.MARKDOWN_V2)


def stats_command(update, context):
    text = f'user_count: {db.count_users()[0]}\n' \
              f'chat_count: {db.count_chats()[0]}\n' \
              f'group_count: {db.count_groups()[0]}'
    context.bot.send_message(update.effective_chat.id, text=text)


def unknown_command(update, context):
    context.bot.send_message(chat_id=update.effective_chat.id, text="Sorry, I didn't understand that command.")


updater = Updater(token=TOKEN, use_context=True)
dispatcher = updater.dispatcher

in_handler = CommandHandler('in', in_command)
all_handler = CommandHandler('all', all_command)
out_handler = CommandHandler('out', out_command)
start_handler = CommandHandler('start', start_command)
stats_handler = CommandHandler('stats', stats_command)
unknown_command = MessageHandler(Filters.command, unknown_command)

dispatcher.add_handler(in_handler)
dispatcher.add_handler(all_handler)
dispatcher.add_handler(out_handler)
dispatcher.add_handler(start_handler)
dispatcher.add_handler(stats_handler)
dispatcher.add_handler(unknown_command)

updater.start_polling()
updater.idle()

db.close()
