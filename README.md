# mention-all-bot

mention-all-bot is a telegram bot that helps to mention all users in a group.

## Usage

1. Use [hosted](https://t.me/mention_all_the_bot) or host yourself

1. Add to your group

1. Everyone who wants to receive notifications opts-in using /in

1. Now you can call everyone with /all

Commands:

```
/start - Display help text
/in - Opt-in to receive mentions
/out - Opt-out of receiving mentions
/all - Mention all opted-in users
/stats - Display bot stats
```

## Installation

```bash
# clone the repo
git clone git@github.com:pischule/mention-all-bot.git

cd mention-all-bot

# install dependencies
pipenv install

# put your bot token into constants.py
echo "TOKEN = '??????????'" > constants.py

# run the bot
pipenv run main.py
```

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## License
GNU GPLv3