# --- Build stage -------------------------------------------------------------
FROM node:20.19-alpine AS build
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npx ng build --configuration production

# --- Runtime stage -------------------------------------------------------------
FROM nginx:alpine

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
# Angular 17+ emits the browser bundle under dist/<project>/browser (same output
# path the gradle ngBuild task copies into Spring's static resources).
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
