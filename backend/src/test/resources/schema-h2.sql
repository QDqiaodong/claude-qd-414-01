-- H2 (MySQL 兼容模式) 测试建表，结构与生产 schema.sql 对齐
SET MODE MySQL;

CREATE TABLE IF NOT EXISTS cell (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(32)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    temp_zone   VARCHAR(16)  NOT NULL,
    capacity    INT          NULL,
    deleted     INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NULL,
    updated_at  DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cell_code (code)
);

CREATE TABLE IF NOT EXISTS location (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(32)  NOT NULL,
    cell_id     BIGINT       NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  DATETIME     NULL,
    updated_at  DATETIME     NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS batch (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    cell_id     BIGINT       NOT NULL,
    cargo       VARCHAR(64)  NOT NULL,
    qty         INT          NULL,
    batch_date  DATE         NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  DATETIME     NULL,
    updated_at  DATETIME     NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS inspection (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    cell_id       BIGINT       NOT NULL,
    check_date    DATE         NOT NULL,
    result        VARCHAR(16)  NOT NULL,
    note          VARCHAR(255) NULL,
    measured_temp DOUBLE       NOT NULL,
    handling      VARCHAR(255) NULL,
    closed        INT          NOT NULL DEFAULT 0,
    closed_at     DATETIME     NULL,
    close_note    VARCHAR(255) NULL,
    created_at    DATETIME     NULL,
    updated_at    DATETIME     NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS inspection_location (
    inspection_id BIGINT NOT NULL,
    location_id   BIGINT NOT NULL,
    PRIMARY KEY (inspection_id, location_id)
);

CREATE TABLE IF NOT EXISTS defrost_window (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    cell_id     BIGINT       NOT NULL,
    start_at    DATETIME     NOT NULL,
    end_at      DATETIME     NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    created_at  DATETIME     NULL,
    updated_at  DATETIME     NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS reservation (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    cell_id         BIGINT       NOT NULL,
    cargo           VARCHAR(64)  NOT NULL,
    qty             INT          NOT NULL,
    plan_date       DATE         NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    confirmed_at    DATETIME     NULL,
    written_off_at  DATETIME     NULL,
    batch_id        BIGINT       NULL,
    created_at      DATETIME     NULL,
    updated_at      DATETIME     NULL,
    PRIMARY KEY (id)
);
