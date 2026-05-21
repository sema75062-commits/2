# Архитектура проекта «Музыкальная школа»

Документ описывает архитектуру веб-приложения и схему базы данных.
Используйте его как точку входа в проект — в нём собраны все ключевые решения.

## Стек технологий

- **Java 21**
- **Spring Boot 3.5.x** — основной фреймворк
- **Spring Web** — REST API
- **Spring Security** — аутентификация и авторизация
- **Spring Data JPA + Hibernate** — ORM
- **PostgreSQL** — база данных
- **Flyway** — миграции БД
- **Lombok** — генерация бойлерплейта
- **Maven** — сборка
- **Frontend** — *решение пока не зафиксировано*: Thymeleaf (SSR) или React/Vue (SPA + REST + JWT)

---

## 1. Общая картина

```
[Браузер]  ──HTTP-запрос──▶  [Spring Boot]  ──SQL──▶  [PostgreSQL]
[Браузер]  ◀──HTTP-ответ───  [Spring Boot]  ◀──данные──
```

Spring Boot:
1. Принимает HTTP-запросы.
2. Выполняет бизнес-логику.
3. Работает с БД через JPA/Hibernate.
4. Возвращает ответ (JSON для API или HTML-страницу).

---

## 2. Слоистая архитектура (Layered Architecture)

Используется классическая трёхслойная архитектура:

```
┌─────────────────────────────────────────────────────────┐
│  Controller          ← принимает HTTP-запросы           │
│   AuthController, StudentController, TeacherController…│
└─────────────────────────────────────────────────────────┘
                       │ вызывает
                       ▼
┌─────────────────────────────────────────────────────────┐
│  Service             ← бизнес-логика                    │
│   UserService, GradeService, CourseService…             │
└─────────────────────────────────────────────────────────┘
                       │ вызывает
                       ▼
┌─────────────────────────────────────────────────────────┐
│  Repository          ← работа с БД                      │
│   UserRepository, GradeRepository…                      │
└─────────────────────────────────────────────────────────┘
                       │ через JPA/Hibernate
                       ▼
                  [PostgreSQL]
```

### Зоны ответственности

| Слой | Что делает | Чего не делает |
|---|---|---|
| **Controller** | принимает запрос, валидирует входные данные, вызывает сервис, формирует ответ | не работает с БД, не содержит бизнес-логики |
| **Service** | бизнес-правила (хэшировать пароль, проверить уникальность email, рассчитать средний балл) | не знает про HTTP, не знает про SQL |
| **Repository** | CRUD-операции с БД через JPA | не содержит бизнес-логики |

### Вспомогательные классы

- **Entity** — Java-класс, соответствующий таблице в БД (`@Entity`).
- **DTO (Data Transfer Object)** — объект для передачи данных через HTTP. Никогда не отдавать Entity напрямую.
- **Config** — конфигурация (Security, CORS).
- **Exception handler** — глобальная обработка ошибок (`@RestControllerAdvice`).
- **Mapper** — преобразование Entity ↔ DTO.

### Структура папок

```
src/main/java/com/example/music_school/
├── MusicSchoolApplication.java   ← точка входа
├── config/                       ← конфигурации (SecurityConfig)
├── controllers/                  ← REST-контроллеры
├── services/                     ← бизнес-логика
├── repositories/                 ← доступ к БД
├── entities/                     ← JPA-сущности
├── dto/                          ← объекты для API
├── mappers/                      ← Entity ↔ DTO (появится позже)
├── exceptions/                   ← кастомные исключения (появится позже)
└── security/                     ← UserDetailsService, JWT (появится позже)

src/main/resources/
├── application.properties
└── db/migration/                 ← Flyway-миграции
```

### Поток данных на примере регистрации

```
1. Браузер → POST /auth/register {email, password}
2. Spring разбирает JSON → RegisterRequest (DTO)
3. AuthController.register(request) → UserService.register(request)
4. UserService:
   - валидация (уникальность email)
   - хэширование пароля через PasswordEncoder
   - сохранение через userRepository.save(user)
5. UserRepository → SQL INSERT INTO users …
6. Ответ → Браузер
```

### Внедрение зависимостей (DI)

- Spring сканирует пакеты и находит классы с `@RestController`, `@Service`, `@Repository`, `@Component`, `@Configuration`.
- Создаёт их экземпляры (бины) в IoC-контейнере.
- Внедряет зависимости через конструктор. `@RequiredArgsConstructor` (Lombok) генерирует конструктор по `final`-полям.

---

## 3. База данных

### 3.1. Реляционная модель

Все данные хранятся в таблицах PostgreSQL. Связи между таблицами — через **foreign key**.

Типы связей:
- **One-to-Many** — у одного преподавателя много занятий. В `lessons` — колонка `teacher_id` → `users.id`.
- **Many-to-Many** — нужна связующая таблица. Пример: `teacher_subjects (teacher_id, subject_id)`.
- **One-to-One** — встречается редко.

### 3.2. Управление схемой через Flyway

Схема БД управляется **Flyway-миграциями**, а не Hibernate `ddl-auto`.

Файлы лежат в `src/main/resources/db/migration/`:

```
V1__create_users_table.sql
V2__create_subjects_and_groups.sql
V3__create_lessons_table.sql
…
```

Настройки в `application.properties`:
```
spring.flyway.enabled=true
spring.jpa.hibernate.ddl-auto=validate
```

`validate` означает: Hibernate проверяет соответствие схемы Entity-классам, но **сам ничего не меняет**.

### 3.3. Сущности и таблицы

#### Ядро: пользователи и справочники

**`users`** — все пользователи (ученики, преподаватели, админы)
| Колонка | Тип | Описание |
|---|---|---|
| id | BIGSERIAL PK | |
| email | VARCHAR UNIQUE NOT NULL | логин |
| password | VARCHAR NOT NULL | хэш пароля (bcrypt) |
| first_name | VARCHAR NOT NULL | |
| last_name | VARCHAR NOT NULL | |
| phone | VARCHAR | |
| role | VARCHAR NOT NULL | STUDENT / TEACHER / ADMIN |
| enabled | BOOLEAN DEFAULT TRUE | для блокировки |
| group_id | BIGINT FK → groups.id | для учеников |
| created_at | TIMESTAMP | |

**`groups`** — учебные группы (опционально, см. вопросы для уточнения)
| Колонка | Тип |
|---|---|
| id | BIGSERIAL PK |
| name | VARCHAR (например, «Фортепиано-1») |

**`subjects`** — предметы
| Колонка | Тип |
|---|---|
| id | BIGSERIAL PK |
| name | VARCHAR |
| description | TEXT |

**`teacher_subjects`** — Many-to-Many: преподаватель ↔ предмет
| teacher_id | subject_id |

#### Расписание и журнал

**`lessons`** — занятия (включают и зачёты/экзамены через поле `type`)
| Колонка | Тип |
|---|---|
| id | BIGSERIAL PK |
| subject_id | FK → subjects.id |
| teacher_id | FK → users.id |
| group_id | FK → groups.id |
| starts_at | TIMESTAMP |
| ends_at | TIMESTAMP |
| classroom | VARCHAR |
| type | VARCHAR (LESSON / EXAM / TEST) |

**`grades`** — оценки
| Колонка | Тип |
|---|---|
| id | BIGSERIAL PK |
| student_id | FK → users.id |
| subject_id | FK → subjects.id |
| lesson_id | FK → lessons.id (nullable) |
| teacher_id | FK → users.id |
| value | INTEGER (1–5) |
| comment | TEXT |
| created_at | TIMESTAMP |

#### Электронные курсы

**`courses`**
| Колонка | Тип |
|---|---|
| id | BIGSERIAL PK |
| subject_id | FK → subjects.id |
| teacher_id | FK → users.id |
| title | VARCHAR |
| description | TEXT |

**`course_materials`**
| Колонка | Тип |
|---|---|
| id | BIGSERIAL PK |
| course_id | FK → courses.id |
| title | VARCHAR |
| content | TEXT |
| file_path | VARCHAR |
| order_index | INTEGER |

**`tests`**
| Колонка | Тип |
|---|---|
| id | BIGSERIAL PK |
| course_id | FK → courses.id |
| title | VARCHAR |
| time_limit_minutes | INTEGER |

**`questions`**
| Колонка | Тип |
|---|---|
| id | BIGSERIAL PK |
| test_id | FK → tests.id |
| text | TEXT |
| type | VARCHAR (SINGLE_CHOICE / MULTI_CHOICE) |

**`answer_options`**
| Колонка | Тип |
|---|---|
| id | BIGSERIAL PK |
| question_id | FK → questions.id |
| text | VARCHAR |
| is_correct | BOOLEAN |

**`test_attempts`** — попытки прохождения теста
| Колонка | Тип |
|---|---|
| id | BIGSERIAL PK |
| test_id | FK → tests.id |
| student_id | FK → users.id |
| started_at | TIMESTAMP |
| finished_at | TIMESTAMP |
| score | INTEGER (автопроверка) |
| teacher_grade | INTEGER (оценка преподавателя) |

**`test_answers`** — ответы ученика на вопросы
| Колонка | Тип |
|---|---|
| id | BIGSERIAL PK |
| attempt_id | FK → test_attempts.id |
| question_id | FK → questions.id |
| answer_option_id | FK → answer_options.id |

#### Публичный контент

**`news`** — новости
**`events`** — концерты, открытые уроки (афиша)
**`gallery_items`** — фотогалерея

Все три имеют `author_id → users.id`, `created_at`, заголовок и поле для контента / пути к файлу.

### 3.4. ER-диаграмма (упрощённая)

```
                         ┌─────────┐
                         │ groups  │
                         └────┬────┘
                              │ 1
                              │
                              │ M
                         ┌────┴────┐         ┌──────────────┐
            M ┌──────────│  users  │M───────M│   subjects   │
              │          └────┬────┘         └──────┬───────┘
              │               │                     │
              ▼               ▼                     ▼
        ┌─────────┐      ┌─────────┐          ┌──────────┐
        │ courses │      │ grades  │◀─────────│ lessons  │
        └────┬────┘      └─────────┘          └──────────┘
             │
       ┌─────┴───────┐
       ▼             ▼
┌─────────────┐  ┌─────────┐
│course_      │  │  tests  │
│materials    │  └────┬────┘
└─────────────┘       │
                      ▼
                ┌──────────┐         ┌─────────────────┐
                │questions │◀────────│ answer_options  │
                └────┬─────┘         └─────────────────┘
                     │
                     ▼
              ┌───────────────┐         ┌──────────────┐
              │ test_attempts │◀────────│ test_answers │
              └───────────────┘         └──────────────┘
```

---

## 4. Аутентификация и авторизация

- Пароли хранятся в виде хэша **bcrypt** (`BCryptPasswordEncoder`).
- Spring Security проверяет, что пользователь авторизован, и сверяет роль.
- Доступ к ручкам ограничивается через `.hasRole(...)` или `@PreAuthorize`.
- `enabled = false` → пользователь заблокирован, не может войти.

Способ передачи учётных данных зависит от выбранного фронтенда:
- **Thymeleaf / SSR** → form-login + session-cookie.
- **SPA + REST** → JWT-токен в заголовке `Authorization: Bearer …`.

---

## 5. Хранение файлов

**Решение**: локальная файловая система сервера в папке `uploads/` (фотографии для галереи, изображения новостей, материалы курсов и т.п.). В БД сохраняется только путь к файлу.

### Принципы

- Папка `uploads/` находится **вне** jar-а, рядом с приложением.
- Папка добавлена в `.gitignore` — пользовательские файлы не попадают в репозиторий.
- Имена файлов генерируются как UUID + оригинальное имя — нет коллизий.
- Лимит размера: `spring.servlet.multipart.max-file-size=10MB`.
- Валидация типа файла: только `image/*`, `application/pdf` и т.п. — белый список.
- Пути собираются безопасно (никаких `..`), путь к файлу нормализуется.

### Абстракция `FileStorageService`

Вся работа с файлами проходит через интерфейс:

```java
public interface FileStorageService {
    String save(MultipartFile file, String subDir);   // вернёт публичный путь / ключ
    Resource load(String path);                       // прочитать файл
    void delete(String path);                         // удалить
}
```

И единственная реализация на этапе MVP — `LocalFileStorageService`, которая работает с папкой `uploads/`.

**Зачем интерфейс**: миграция на S3 в будущем = добавить `S3FileStorageService` и переключить активный бин в конфигурации. Никаких изменений в контроллерах, сервисах и БД не нужно.

### Структура папок

```
project-root/
├── uploads/                  ← вне jar, в .gitignore
│   ├── gallery/
│   ├── news/
│   └── courses/
└── src/
```

### Конфигурация

```properties
app.upload.dir=./uploads
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

Раздача статики: папка `uploads/` мапится на URL `/uploads/**` через `WebMvcConfigurer.addResourceHandlers(...)`.

---

## 6. Открытые вопросы

Перед стартом этапа 1 нужно зафиксировать:

1. **Frontend**: Thymeleaf (SSR) или SPA + REST (JWT)?
2. **Группы**: использовать сущность `groups` или привязывать ученика напрямую к занятиям?
3. **Профили**: один `application.properties` или разделение на `dev`/`prod`?

### Зафиксированные решения

- **Хранение файлов**: локальная файловая система через абстракцию `FileStorageService` (см. раздел 5). Реализация: `LocalFileStorageService`. Возможна миграция на S3 без изменений в коде потребителей.
