CREATE TYPE "service_type" AS ENUM (
  'DXO',
  'REG',
  'NSF'
);

CREATE TYPE "quiz_question_type" AS ENUM (
  'single',
  'multiple'
);

CREATE TABLE "users" (
  "id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  "idp_id" varchar UNIQUE NOT NULL,
  "preferred_name" varchar NOT NULL,
  "service_type" service_type,
  "created_at" timestamp NOT NULL DEFAULT 'now()',
  "offline_created_at" timestamp
);

CREATE TABLE "users_article_reads" (
  "user_id" integer,
  "article_id" integer,
  "read_at" timestamp NOT NULL DEFAULT 'now()',
  PRIMARY KEY ("user_id", "article_id", "read_at")
);

CREATE TABLE "users_meditation_track_listens" (
  "user_id" integer,
  "meditation_track_id" integer,
  "listened_at" timestamp NOT NULL DEFAULT 'now()',
  PRIMARY KEY ("user_id", "meditation_track_id", "listened_at")
);

CREATE TABLE "users_quiz_question_answers" (
  "id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  "user_id" integer NOT NULL,
  "quiz_id" integer NOT NULL,
  "question_index" integer NOT NULL,
  "choice_index" integer NOT NULL
);

CREATE TABLE "diary_entries" (
  "user_id" integer,
  "entry_date" date,
  "created_at" timestamp NOT NULL DEFAULT 'now()',
  "offline_created_at" timestamp,
  "updated_at" timestamp NOT NULL DEFAULT 'now()',
  "offline_updated_at" timestamp,
  "mood" integer NOT NULL,
  "significant_events" varchar[],
  "moment_best" varchar[],
  "moment_worst" varchar[],
  "what_helped" varchar[],
  "medicine_taken" varchar[],
  "nap_count" integer,
  "nap_duration_total_hrs" integer,
  "sleep_start_at" timestamp,
  "sleep_end_at" timestamp,
  "tags" varchar[],
  PRIMARY KEY ("user_id", "entry_date")
);

CREATE TABLE "alcohol_caffeine_intakes" (
  "user_id" integer,
  "entry_date" date,
  "count" integer,
  "unit" varchar
);

CREATE TABLE "articles" (
  "id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  "public_id" uuid NOT NULL DEFAULT gen_random_uuid(),
  "category_id" integer,
  "title" varchar NOT NULL,
  "creator_id" integer NOT NULL,
  "updater_id" integer NOT NULL,
  "created_at" timestamp NOT NULL DEFAULT 'now()',
  "updated_at" timestamp NOT NULL DEFAULT 'now()',
  "quiz_id" integer
);

CREATE TABLE "article_pages" (
  "article_id" integer,
  "page_index" integer NOT NULL,
  "title" varchar NOT NULL,
  "body" varchar NOT NULL,
  "created_at" timestamp NOT NULL DEFAULT 'now()',
  "updated_at" timestamp NOT NULL DEFAULT 'now()',
  PRIMARY KEY ("article_id", "page_index")
);

CREATE TABLE "meditation_tracks" (
  "id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  "public_id" uuid NOT NULL DEFAULT gen_random_uuid(),
  "title" varchar UNIQUE NOT NULL,
  "file_location" varchar NOT NULL
);

CREATE TABLE "categories" (
  "id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  "public_id" uuid NOT NULL DEFAULT gen_random_uuid(),
  "title" varchar UNIQUE NOT NULL,
  "icon_download_url" varchar NOT NULL
);

CREATE TABLE "quizzes" (
  "id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  "public_id" uuid NOT NULL DEFAULT gen_random_uuid(),
  "title" varchar UNIQUE NOT NULL
);

CREATE TABLE "quiz_questions" (
  "quiz_id" integer,
  "index" integer,
  "title" varchar NOT NULL,
  "type" quiz_question_type NOT NULL,
  PRIMARY KEY ("quiz_id", "index")
);

CREATE TABLE "quiz_question_choices" (
  "quiz_id" integer,
  "question_index" integer,
  "index" integer,
  "title" varchar UNIQUE NOT NULL,
  PRIMARY KEY ("quiz_id", "question_index", "index")
);

CREATE TABLE "quotes" (
  "id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  "public_id" uuid NOT NULL DEFAULT gen_random_uuid(),
  "author_id" integer NOT NULL,
  "created_at" timestamp NOT NULL DEFAULT 'now()',
  "quote" varchar UNIQUE NOT NULL
);

CREATE TABLE "feedback" (
  "id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  "public_id" uuid NOT NULL DEFAULT gen_random_uuid(),
  "category_id" integer NOT NULL,
  "author_id" integer NOT NULL,
  "created_at" timestamp NOT NULL DEFAULT 'now()'
);

CREATE TABLE "feedback_categories" (
  "id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  "public_id" uuid NOT NULL DEFAULT gen_random_uuid(),
  "title" varchar UNIQUE NOT NULL
);

ALTER TABLE "feedback" ADD FOREIGN KEY ("author_id") REFERENCES "users" ("id");
ALTER TABLE "feedback" ADD FOREIGN KEY ("category_id") REFERENCES "feedback_categories" ("id");

-- -----

ALTER TABLE "users_article_reads" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");

ALTER TABLE "users_article_reads" ADD FOREIGN KEY ("article_id") REFERENCES "articles" ("id");

ALTER TABLE "users_meditation_track_listens" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");

ALTER TABLE "users_meditation_track_listens" ADD FOREIGN KEY ("meditation_track_id") REFERENCES "meditation_tracks" ("id");

ALTER TABLE "users_quiz_question_answers" ADD FOREIGN KEY ("quiz_id", "question_index", "choice_index") REFERENCES "quiz_question_choices" ("quiz_id", "question_index", "index");

ALTER TABLE "diary_entries" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");

ALTER TABLE "alcohol_caffeine_intakes" ADD FOREIGN KEY ("user_id", "entry_date") REFERENCES "diary_entries" ("user_id", "entry_date");

ALTER TABLE "articles" ADD FOREIGN KEY ("category_id") REFERENCES "categories" ("id");

ALTER TABLE "articles" ADD FOREIGN KEY ("creator_id") REFERENCES "users" ("id");

ALTER TABLE "articles" ADD FOREIGN KEY ("updater_id") REFERENCES "users" ("id");

ALTER TABLE "articles" ADD FOREIGN KEY ("quiz_id") REFERENCES "quizzes" ("id");

ALTER TABLE "article_pages" ADD FOREIGN KEY ("article_id") REFERENCES "articles" ("id");

ALTER TABLE "quiz_questions" ADD FOREIGN KEY ("quiz_id") REFERENCES "quizzes" ("id");

ALTER TABLE "quiz_question_choices" ADD FOREIGN KEY ("quiz_id", "question_index") REFERENCES "quiz_questions" ("quiz_id", "index");

ALTER TABLE "quotes" ADD FOREIGN KEY ("author_id") REFERENCES "users" ("id");

