# mention-all-bot

mention-all-bot is a telegram bot that helps to mention all users in a group.

## Usage

1. Use [hosted](https://t.me/mention_all_the_bot?startgroup) or host yourself

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

`docker-compose.yml`:
```yaml
services:
  app:
    image: ghcr.io/pischule/mention-all-bot:main
    restart: always
    environment:
      TGBOT_TOKEN: "<< bot token >>"
      DB_PWD: "<< db password >>"
  db:
    image: postgres
    restart: always
    environment:
      POSTGRES_PASSWORD: "<< db password >>"
    volumes:
      - postgres-data:/var/lib/postgresql/data
volumes:
  postgres-data:
```

```shell
docker compose up -d
```

## License
GNU GPLv3
