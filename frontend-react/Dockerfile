# Stage 1: build del frontend React con Vite
FROM node:20-alpine AS build
ARG VITE_AZURE_CLIENT_ID
ARG VITE_AZURE_AUTHORITY
ARG VITE_AZURE_REDIRECT_URI
ARG VITE_AZURE_API_SCOPE
ARG VITE_API_BASE_URL
ENV VITE_AZURE_CLIENT_ID=$VITE_AZURE_CLIENT_ID \
    VITE_AZURE_AUTHORITY=$VITE_AZURE_AUTHORITY \
    VITE_AZURE_REDIRECT_URI=$VITE_AZURE_REDIRECT_URI \
    VITE_AZURE_API_SCOPE=$VITE_AZURE_API_SCOPE \
    VITE_API_BASE_URL=$VITE_API_BASE_URL
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: servidor estatico nginx. La config /etc/nginx/templates/*.template
# se procesa automaticamente con envsubst al arrancar usando las variables de
# entorno (API_GATEWAY_URL -> IP del EC2 del backend).
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template
EXPOSE 80