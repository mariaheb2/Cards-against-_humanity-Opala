FROM node:20-alpine

WORKDIR /app

# Copiar package.json e instalar dependências
COPY package*.json ./
RUN npm ci --only=production

# Copiar o proxy e os arquivos estáticos
COPY proxy.js .
COPY view ./view

# Expõe a porta unificada (HTTP + WebSocket)
EXPOSE 8082

# Variáveis de ambiente 
ENV TCP_HOST=java-server
ENV TCP_PORT=8080
ENV HTTP_PORT=8082

CMD ["node", "proxy.js"]