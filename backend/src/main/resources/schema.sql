-- 冷库管理系统 · 建表 + 种子数据
-- 通过 mysql 容器 initdb 挂载执行（库名 cold_storage 由 MYSQL_DATABASE 创建）

SET NAMES utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS location (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(32)  NOT NULL,
    cell_id     BIGINT       NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  DATETIME     NULL,
    updated_at  DATETIME     NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    PRIMARY KEY (id),
    KEY idx_inspection_cell_open (cell_id, result, closed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 巡检点检货位：一张巡检单至少点一个货位
CREATE TABLE IF NOT EXISTS inspection_location (
    inspection_id BIGINT NOT NULL,
    location_id   BIGINT NOT NULL,
    PRIMARY KEY (inspection_id, location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 化霜占窗：挂在未软删的库间上，同一库间重叠时段只允许一扇
CREATE TABLE IF NOT EXISTS defrost_window (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    cell_id     BIGINT       NOT NULL,
    start_at    DATETIME     NOT NULL,
    end_at      DATETIME     NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    created_at  DATETIME     NULL,
    updated_at  DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_defrost_cell_time (cell_id, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 入库预占：待确认 → 已确认 → 已核销；只有已确认未核销的箱数占用剩余可收量
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
    PRIMARY KEY (id),
    KEY idx_reservation_cell_status (cell_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== 种子数据 =====

-- 库间（cell）
DELETE FROM cell;
INSERT INTO cell (id, code, name, temp_zone, capacity, deleted, created_at, updated_at) VALUES
(1, 'A库-01', '一号冷冻主库', '冷冻', 500, 0, '2026-09-10 09:00:00', '2026-09-10 09:00:00'),
(2, 'A库-02', '二号冷藏库',   '冷藏', 300, 0, '2026-09-10 09:00:00', '2026-09-10 09:00:00'),
(3, 'B库-01', '三号冷冻库',   '冷冻', 800, 0, '2026-09-10 09:00:00', '2026-09-10 09:00:00'),
(4, 'B库-02', '四号冷藏库',   '冷藏', 400, 0, '2026-09-10 09:00:00', '2026-09-10 09:00:00'),
(5, 'C库-01', '五号冷冻库',   '冷冻', 600, 0, '2026-09-10 09:00:00', '2026-09-10 09:00:00'),
(6, 'C库-02', '六号冷藏库',   '冷藏', 350, 0, '2026-09-10 09:00:00', '2026-09-10 09:00:00');

-- 货位（location）
DELETE FROM location;
INSERT INTO location (id, code, cell_id, status, created_at, updated_at) VALUES
(1, 'L-A01-1', 1, '空',   '2026-09-10 09:10:00', '2026-09-10 09:10:00'),
(2, 'L-A01-2', 1, '占用', '2026-09-10 09:10:00', '2026-09-10 09:10:00'),
(3, 'L-A01-3', 1, '空',   '2026-09-10 09:10:00', '2026-09-10 09:10:00'),
(4, 'L-A02-1', 2, '占用', '2026-09-10 09:10:00', '2026-09-10 09:10:00'),
(5, 'L-A02-2', 2, '空',   '2026-09-10 09:10:00', '2026-09-10 09:10:00'),
(6, 'L-B01-1', 3, '占用', '2026-09-10 09:10:00', '2026-09-10 09:10:00'),
(7, 'L-B01-2', 3, '空',   '2026-09-10 09:10:00', '2026-09-10 09:10:00'),
(8, 'L-C01-1', 5, '空',   '2026-09-10 09:10:00', '2026-09-10 09:10:00'),
(9, 'L-B02-1', 4, '空',   '2026-09-10 09:10:00', '2026-09-10 09:10:00'),
(10, 'L-C02-1', 6, '空',  '2026-09-10 09:10:00', '2026-09-10 09:10:00');

-- 入库批次（batch）
DELETE FROM batch;
INSERT INTO batch (id, cell_id, cargo, qty, batch_date, status, created_at, updated_at) VALUES
(1, 1, '三文鱼',   120, '2026-09-10', '在库', '2026-09-10 10:00:00', '2026-09-10 10:00:00'),
(2, 2, '鲜牛奶',    80, '2026-09-12', '待入', '2026-09-12 10:00:00', '2026-09-12 10:00:00'),
(3, 3, '澳洲牛排', 200, '2026-08-30', '已出', '2026-08-30 10:00:00', '2026-09-05 10:00:00'),
(4, 3, '北极虾',   150, '2026-09-15', '在库', '2026-09-15 10:00:00', '2026-09-15 10:00:00'),
(5, 4, '有机蔬菜',  60, '2026-09-14', '待入', '2026-09-14 10:00:00', '2026-09-14 10:00:00'),
(6, 5, '冰淇淋',   300, '2026-09-01', '已出', '2026-09-01 10:00:00', '2026-09-08 10:00:00'),
(7, 6, '速冻水饺', 210, '2026-09-16', '在库', '2026-09-16 10:00:00', '2026-09-16 10:00:00');

-- 巡检（inspection）：每单都点货位、记实测温度；异常单带处置意见。
-- 温区上下限（质检口径）：冷冻 -25.0 ~ -15.0℃，冷藏 0.0 ~ 8.0℃。
-- #2 冷藏库温度未越界但压缩机异响 → 异常且未闭环（演示开在半路的异常单）；
-- #8 异常已闭环留痕（closed=1）。
DELETE FROM inspection_location;
DELETE FROM inspection;
INSERT INTO inspection (id, cell_id, check_date, result, note, measured_temp, handling, closed, closed_at, close_note, created_at, updated_at) VALUES
(1, 1, '2026-09-18', '正常', '温度 -18℃，运行正常',   -18.0, NULL, 0, NULL, NULL, '2026-09-18 08:00:00', '2026-09-18 08:00:00'),
(2, 2, '2026-09-18', '异常', '制冷压缩机异响',         3.0, '已报修压缩机，等待配件到货', 0, NULL, NULL, '2026-09-18 08:00:00', '2026-09-18 08:00:00'),
(3, 3, '2026-09-18', '正常', '温湿度达标',            -20.0, NULL, 0, NULL, NULL, '2026-09-18 08:00:00', '2026-09-18 08:00:00'),
(4, 4, '2026-09-18', '正常', '运行正常',               4.0, NULL, 0, NULL, NULL, '2026-09-18 08:00:00', '2026-09-18 08:00:00'),
(5, 5, '2026-09-17', '正常', '例行巡检正常',          -19.0, NULL, 0, NULL, NULL, '2026-09-17 08:00:00', '2026-09-17 08:00:00'),
(6, 6, '2026-09-17', '正常', '例行巡检正常',            5.0, NULL, 0, NULL, NULL, '2026-09-17 08:00:00', '2026-09-17 08:00:00'),
(7, 1, '2026-09-16', '正常', '历史巡检记录',          -18.5, NULL, 0, NULL, NULL, '2026-09-16 08:00:00', '2026-09-16 08:00:00'),
(8, 3, '2026-09-16', '异常', '库门密封条破损',        -17.0, '已登记更换密封条', 1, '2026-09-16 15:30:00', '密封条已更换，复测温度正常', '2026-09-16 08:00:00', '2026-09-16 15:30:00');

INSERT INTO inspection_location (inspection_id, location_id) VALUES
(1, 1), (2, 4), (3, 6), (4, 9), (5, 8), (6, 10), (7, 2), (8, 7);

-- 化霜占窗（defrost_window）：
-- 1 号冷冻库一扇已结束（时长 60 分钟 ≥ 冷冻下限 40）；
-- 2 号冷藏库一扇进行中（演示剩余可收箱数按 0、预占不能确认、批次不能入库）；
-- 6 号冷藏库一扇未开始（可与「进行中」对照，边界相接不算重叠）。
DELETE FROM defrost_window;
INSERT INTO defrost_window (id, cell_id, start_at, end_at, reason, created_at, updated_at) VALUES
(1, 1, '2026-09-19 08:00:00', '2026-09-19 09:00:00', '蒸发器定期化霜', '2026-09-18 16:00:00', '2026-09-18 16:00:00'),
(2, 2, '2026-09-20 12:00:00', '2026-09-20 15:00:00', '冷藏风机冲霜，暂停收货', '2026-09-19 18:00:00', '2026-09-19 18:00:00'),
(3, 6, '2026-09-21 08:00:00', '2026-09-21 08:30:00', '例行化霜', '2026-09-20 09:00:00', '2026-09-20 09:00:00');

-- 入库预占：默认不给种子，由值班在页面上开立、确认，便于按验收场景走完整流程
DELETE FROM reservation;
