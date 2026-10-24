# --- Etapa de build: Node 24 + Angular CLI ---
FROM node:24.22.1-alpine AS build
WORKDIR /app
ENV NG_CLI_ANALYTICS=false

# Primero solo lo que define las dependencias: esta capa se reutiliza mientras no cambie el lock.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# --- Etapa final: nginx sirve los estáticos y hace de proxy de /api/ ---
FROM nginx:1.30.0-alpine

# Sustituye el server por defecto; el nginx.conf principal de la imagen lo incluye desde conf.d.
COPY nginx.conf /etc/nginx/conf.d/default.conf
# angular.json no fija outputPath: el builder application deja el navegador en dist/<proyecto>/browser.
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
