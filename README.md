# PoE Nexus

Кооперативный дашборд для групп игроков Path of Exile (real-time).

## Стек
- Backend: Vert.x (Kotlin, JDK 17), PostgreSQL, Redis
- Frontend: Vue 3 + TypeScript + Pinia + Vue Router + Tailwind CSS

## Структура
- `backend/` — Vert.x приложение (модульный монолит, вертиклы)
- `frontend/` — Vue 3 SPA
- `docker/` — PostgreSQL + Redis для локальной разработки
- `db/migrations/` — SQL-миграции

## Быстрый старт
1. Поднять БД: `docker compose -f docker/docker-compose.yml up -d`
2. Backend: `cd backend && ./gradlew run`
3. Frontend: `cd frontend && npm install && npm run dev`

Frontend: http://localhost:5173 (прокси `/api` и `/ws` -> :8080)