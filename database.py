import os
import psycopg2

DB_PWD = os.environ['DB_PWD']


class BotDatabase:
    def __init__(self, filename):
        self.conn = psycopg2.connect(
            f"dbname='postgres' user='postgres' host='db' password='{DB_PWD}'")

        self._add_users_table()
        self._add_chats_table()

    def add_user(self, user_id, username):
        with self.conn.cursor() as cursor:
            query = '''INSERT INTO users (user_id, username) VALUES (%s, %s) ON CONFLICT (user_id) do UPDATE SET username = %s'''
            cursor.execute(query, (user_id, username, username))
            self.conn.commit()

    def get_all_users(self):
        with self.conn.cursor() as cursor:
            query = '''SELECT user_id, username FROM users'''
            cursor.execute(query)
            return cursor.fetchall()

    def get_users_from_chat(self, group_id):
        with self.conn.cursor() as cursor:
            query = '''SELECT c.user_id, u.username 
                        FROM chats c 
                        JOIN users u on c.user_id = u.user_id 
                        WHERE c.chat_id=%s'''
            cursor.execute(query, (group_id,))
            return cursor.fetchall()

    def add_user_to_chat(self, chat_id, user_id):
        with self.conn.cursor() as cursor:
            query = '''INSERT INTO chats (chat_id, user_id) VALUES (%s, %s) ON CONFLICT DO NOTHING'''
            cursor.execute(query, (chat_id, user_id))
            self.conn.commit()

    def delete_user_from_chat(self, chat_id, user_id):
        with self.conn.cursor() as cursor:
            query = '''DELETE from chats WHERE chat_id = %s AND user_id = %s'''
            cursor.execute(query, (chat_id, user_id))
            self.conn.commit()

    def update_user_username(self, user_id, new_username):
        with self.conn.cursor() as cursor:
            sql_update_query = '''UPDATE users SET username = %s WHERE user_id = %s'''
            cursor.execute(sql_update_query, (new_username, user_id))
            self.conn.commit()

    def count_users(self):
        with self.conn.cursor() as cursor:
            query = '''SELECT COUNT(user_id) FROM users'''
            cursor.execute(query)
            return cursor.fetchone()

    def count_chats(self):
        with self.conn.cursor() as cursor:
            query = '''SELECT COUNT(DISTINCT chat_id) FROM chats'''
            cursor.execute(query)
            return cursor.fetchone()

    def count_groups(self):
        with self.conn.cursor() as cursor:
            query = '''SELECT COUNT(DISTINCT chat_id) FROM chats WHERE chat_id <> user_id'''
            cursor.execute(query)
            return cursor.fetchone()

    def _add_users_table(self):
        with self.conn.cursor() as cursor:
            query = '''CREATE TABLE IF NOT EXISTS 
                                        users (user_id BIGINT, username VARCHAR(64), PRIMARY KEY (user_id))'''
            cursor.execute(query)
            self.conn.commit()

    def _add_chats_table(self):
        with self.conn.cursor() as cursor:
            query = '''
                CREATE TABLE IF NOT EXISTS chats (
                    chat_id BIGINT, 
                    user_id BIGINT, 
                    PRIMARY KEY (chat_id, user_id),
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE )'''
            cursor.execute(query)
            self.conn.commit()

    def close(self):
        self.conn.close()


if __name__ == '__main__':
    db = BotDatabase('bot_users.db')
    db.close()
