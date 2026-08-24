import { createRouter, createWebHistory } from 'vue-router'

// Каркас роутера. Маршруты заполняются по мере
// разработки модулей. Модуль авторизации добавит
// /login, /register и guard beforeEach для JWT.
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // TODO: маршруты модулей
  ]
})

export default router