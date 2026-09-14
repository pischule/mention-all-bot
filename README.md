# mention-all-bot

mention-all-bot is a telegram bot that helps to mention all users in a group.

## Usage

1. Use [hosted](https://t.me/mention_all_the_bot?startgroup) or host yourself

1. Add to your group

1. Everyone who wants to receive notifications opts-in using /in

1. Now you can call everyone with /all

Commands:

```
/start        - Display help text
/in           - Opt-in to receive mentions
/out          - Opt-out of receiving mentions
/all          - Mention all opted-in users
/stats        - Display users and chats stats
/stats_recent - Display recent activity stats
```

## Installation

The bot stores its data in a SQLite database and reads configuration from
`config/application.properties`. Create it next to `docker-compose.yml`:

`config/application.properties`:
```properties
jdbc-url=jdbc:sqlite:data/db.sqlite3
bot-token=<BOT_TOKEN>
```

`docker-compose.yml`:
```yaml
services:
  app:
    image: ghcr.io/pischule/mention-all-bot:master
    restart: always
    volumes:
      - ./config:/app/config:ro
      - ./data:/app/data
```

```shell
docker compose up -d
```

The `data` directory holds the SQLite database, keep it to preserve state
between restarts.

## License
GNU GPLv3
