import psycopg2
from psycopg2 import sql
import random
import string
import datetime
import threading

# Configuração da conexão com o banco de dados
conn_string = "postgresql://rinha:rinhadebackend@localhost:5432/app_db?sslmode=disable"

# Função para gerar um nome aleatório
def random_name():
    return ''.join(random.choices(string.ascii_letters, k=10))

# Função para inserções em uma sessão
def insert_records(session_id):
    conn = psycopg2.connect(conn_string)
    cursor = conn.cursor()
    try:
        for _ in range(10000):
            name = random_name()
            description = "Description for " + name
            created_at = datetime.datetime.now()
    
            query = sql.SQL("INSERT INTO items (name, description, created_at) VALUES (%s, %s, %s)")
            cursor.execute(query, (name, description, created_at))
        print(f"Sessão {session_id}: Inserções concluídas")
    except Exception as e:
        print(f"Sessão {session_id}: Erro na inserção:", e)
    finally:
        conn.commit()
        conn.close()

# Criar e iniciar sessões
threads = []
for i in range(100):
    thread = threading.Thread(target=insert_records, args=(i,))
    threads.append(thread)
    thread.start()

# Aguardar todas as sessões terminarem
for thread in threads:
    thread.join()

print("Todas as sessões concluídas")
