import psycopg2
import random
import string
import datetime

# Configuração da conexão com o banco de dados
conn_string = "postgresql://rinha:rinhadebackend@localhost:5432/app_db?sslmode=disable"
conn = psycopg2.connect(conn_string)
cursor = conn.cursor()

# Consulta pesada
query = """
SELECT
    items.id,
    items.name,
    items.description,
    items.created_at,
    generate_series(1, 1000000) AS heavy_column
FROM
    items
ORDER BY
    random()
LIMIT
    100
"""

# Executar consulta
cursor.execute(query)
rows = cursor.fetchall()
for row in rows:
    print(row)

# Fechar conexão
conn.close()
