CREATE TABLE books (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id      VARCHAR(255)  NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    author        VARCHAR(200)  NOT NULL,
    cover_url     VARCHAR(2048),
    rating        REAL          NOT NULL CHECK (rating >= 0.5 AND rating <= 5),
    review        VARCHAR(5000) NOT NULL,
    date_finished DATE          NOT NULL
);

CREATE INDEX idx_books_owner_id ON books (owner_id);
