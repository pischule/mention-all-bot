import sqlite3


class BotDatabase:
    def __init__(self, filename):
        self.conn = sqlite3.connect(filename, check_same_thread=False)

        self._add_users_table()
        self._add_chats_table()

    def add_user(self, user_id, username):
        cursor = self.conn.cursor()
        sql_insert_query = '''INSERT OR IGNORE INTO users (user_id, username) VALUES (?, ?);'''
        sql_update_query = '''UPDATE users SET username = ? WHERE user_id = ?;'''
        cursor.execute(sql_insert_query, (user_id, username))
        cursor.execute(sql_update_query, (username, user_id))
        self.conn.commit()
        cursor.close()

    def get_all_users(self):
        cursor = self.conn.cursor()
        sql_query = '''SELECT user_id, username FROM users;'''
        cursor.execute(sql_query)
        records = cursor.fetchall()
        cursor.close()
        return records

    def get_users_from_chat(self, group_id):
        cursor = self.conn.cursor()
        sql_query = '''SELECT c.user_id, u.username 
                       FROM chats c 
                       JOIN users u on c.user_id = u.user_id 
                       WHERE c.chat_id=?;'''
        cursor.execute(sql_query, (group_id,))
        records = cursor.fetchall()
        cursor.close()
        return records

    def add_user_to_chat(self, chat_id, user_id):
        cursor = self.conn.cursor()
        sql_query = '''INSERT OR IGNORE INTO chats (chat_id, user_id) VALUES (?, ?);'''
        cursor.execute(sql_query, (chat_id, user_id))
        self.conn.commit()
        cursor.close()

    def delete_user_from_chat(self, chat_id, user_id):
        cursor = self.conn.cursor()
        sql_query = '''DELETE from chats WHERE chat_id=? AND user_id=?;'''
        cursor.execute(sql_query, (chat_id, user_id))
        self.conn.commit()
        cursor.close()

    def update_user_username(self, user_id, new_username):
        cursor = self.conn.cursor()
        sql_update_query = '''UPDATE users SET username=? WHERE user_id=?;'''
        cursor.execute(sql_update_query, (new_username, user_id))
        self.conn.commit()
        cursor.close()

    def count_users(self):
        cursor = self.conn.cursor()
        sql_query = '''SELECT COUNT(user_id) FROM users;'''
        cursor.execute(sql_query)
        count = cursor.fetchone()
        cursor.close()
        return count

    def count_chats(self):
        cursor = self.conn.cursor()
        sql_query = '''SELECT COUNT(DISTINCT chat_id) FROM chats;'''
        cursor.execute(sql_query)
        count = cursor.fetchone()
        cursor.close()
        return count

    def count_groups(self):
        cursor = self.conn.cursor()
        sql_query = '''SELECT COUNT(DISTINCT chat_id) FROM chats WHERE chat_id <> user_id;'''
        cursor.execute(sql_query)
        count = cursor.fetchone()
        cursor.close()
        return count

    def _add_users_table(self):
        cursor = self.conn.cursor()
        sql_query = '''CREATE TABLE IF NOT EXISTS 
                                    users (user_id INT, username VARCHAR(64), PRIMARY KEY (user_id));'''
        cursor.execute(sql_query)
        self.conn.commit()
        cursor.close()


    def _add_chats_table(self):
        cursor = self.conn.cursor()
        sql_query = '''
            CREATE TABLE IF NOT EXISTS chats (
                chat_id INT, 
                user_id INT, 
                PRIMARY KEY (chat_id, user_id),
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE );'''
        cursor.execute(sql_query)
        self.conn.commit()
        cursor.close()

    def close(self):
        self.conn.close()


if __name__ == '__main__':
    db = BotDatabase('bot_users.db')
    db.close()
