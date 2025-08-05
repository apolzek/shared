# Dockerfile
FROM python:3.9-slim

# Definir diretório de trabalho
WORKDIR /app

# Copiar arquivo de dependências
COPY requirements.txt .

# Instalar dependências
RUN pip install --no-cache-dir -r requirements.txt

# Copiar código da aplicação
COPY . .

# Expor porta
EXPOSE 8000

# Variáveis de ambiente
ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1

# Comando para executar a aplicação
CMD ["python", "infer.py"]