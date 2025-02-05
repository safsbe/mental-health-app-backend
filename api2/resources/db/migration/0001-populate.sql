INSERT INTO "users" ("idp_id", "preferred_name", "service_type")
VALUES ('0001', 'John Tan', 'NSF');

INSERT INTO "categories" ("title", "icon_download_url")
VALUES ('Category 1', 'https://blob.dev.lumin.itserv.com.sg/c82210cb-3a7c-4b24-9b5f-46736238af94');

INSERT INTO "articles" ("category_id", "title", "creator_id", "updater_id")
VALUES (1, 'Article 1', 1, 1);

INSERT INTO "article_pages" ("article_id", "page_index", "title", "body")
VALUES (1, 1, 'Aritcle 1 Page 2 Title', 'Article 1 Page 1 Body'),
       (1, 2, 'Article 1 Page 2 Title', 'Article 1 Page 2 Body');

INSERT INTO "meditation_tracks" ("title", "file_location")
VALUES ('Meditation Track 1', 'https://blob.dev.lumin.itserv.com.sg/364b8206-b24d-406e-9ae8-8e0ab90e013a');
 
INSERT INTO "diary_entries" ("user_id", "entry_date", "mood", "tags")
VALUES (1, '2024-01-01', 5, ARRAY ['foo', 'bar']);
