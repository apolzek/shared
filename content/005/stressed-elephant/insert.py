import psycopg2
from psycopg2 import sql
import random
import string
import datetime

# Configuração da conexão com o banco de dados
conn_string = "postgresql://rinha:rinhadebackend@localhost:5432/app_db?sslmode=disable"
conn = psycopg2.connect(conn_string)
cursor = conn.cursor()

# Função para gerar um nome aleatório
def random_name():
    return ''.join(random.choices(string.ascii_letters, k=10))

# Inserções na tabela "items"
for _ in range(100000000000000000):
    name = random_name()
    description = "Description for " + name
    created_at = datetime.datetime.now()

    query = sql.SQL("INSERT INTO items (name, description, created_at) VALUES (%s, %s, %s)")
    try:
        cursor.execute(query, (name, description, created_at))
        print("Inserção bem-sucedida:", name)
    except Exception as e:
        print("Erro na inserção de", name, ":", e)

# Commit e fechamento da conexão
conn.commit()
conn.close()
