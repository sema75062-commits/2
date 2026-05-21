# Проект: Веб-платформа для музыкальной школы

## Стек

- Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL, Flyway, Lombok, Maven
- Frontend: решение пока не зафиксировано (Thymeleaf SSR или SPA + REST + JWT)

## Роли

- STUDENT (ученик)
- TEACHER (преподаватель)
- ADMIN (администратор)

## Ключевые подсистемы

1. **Аутентификация/авторизация** — регистрация (только STUDENT), логин, логаут, ролевой доступ
2. **Публичная часть** — главная, о школе, преподаватели, новости, галерея, контакты, афиша
3. **ЛК ученика** — расписание, журнал оценок, доступные курсы, профиль
4. **ЛК преподавателя** — список учеников, журнал, курсы/тесты, расписание, профиль
5. **Электронные курсы** — материалы, тесты (single/multi choice), автопроверка + ручная корректировка
6. **Журнал успеваемости** — оценки по предметам, ввод преподавателем, просмотр учеником
7. **Администрирование** — CRUD пользователей, блокировка, справочники

## Основные сущности БД

users, groups, subjects, teacher_subjects, lessons, grades, courses, course_materials, tests, questions, answer_options, test_attempts, test_answers, news, events, gallery_items

## Архитектура

- Трёхслойная: Controller → Service → Repository
- Структура пакетов: config, controllers, services, repositories, entities, dto, exceptions, mappers, security
- Миграции через Flyway (ddl-auto=validate)
- Пароли — bcrypt
- Файлы (галерея, новости, материалы курсов): локальная ФС (`uploads/`) через абстракцию `FileStorageService` — единственная реализация на старте `LocalFileStorageService`, миграция на S3 в будущем без изменений в потребителях

## Текущий этап разработки

Этап 0 (Инфраструктура): включить Flyway, первая миграция, вынести пароли в env, подключить validation.

## Полная документация

- Требования: #[[file:docs/REQUIREMENTS.md]]
- Архитектура и БД: #[[file:docs/ARCHITECTURE.md]]
- План разработки: #[[file:docs/DEVELOPMENT_PLAN.md]]
