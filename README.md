# 冷库管理系统（claude-qd-414）

全栈「种子仓」：Spring Boot 3.3 + Vue3 + Element Plus + MySQL8 + Redis7，docker compose 一键起。

## 技术要点（差异化）
- **JPA + 软删除**：`cell` 实体用 `@SQLDelete(sql="UPDATE cell SET deleted=1 WHERE id=?")` + `@Where(clause="deleted=0")`，`list` 自动过滤已删记录。
- **JPA 审计**：`@EnableJpaAuditing` + `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate`/`@LastModifiedDate`，自动记录创建/修改时间。

## 模块
1. **库间 cell**（软删）：编号唯一；温区（冷冻/冷藏）；容量。软删后 `list` 查不到，但其货位仍可关联。
2. **货位 location**：编号；归属库间；状态（空/占用）。归属库间必须存在且未软删。
3. **入库批次 batch**：货品名；数量；入库日期；状态机（待入→在库→已出）。入库必须填数量。
4. **巡检 inspection**：库间（未软删）；巡检日期；结果（正常/异常）；备注；**至少点选一个本库间货位 + 必填实测温度**；同库间同天只留一条。
   - 温区上下限（质检口径）：冷冻 **-25.0 ~ -15.0℃**、冷藏 **0.0 ~ 8.0℃**；实测温度越界必须记「异常」并写处置意见，否则当场驳回；任何异常单都必须写处置意见；
   - 异常单未闭环前一直开着：不能删除、不能改回「正常」，只能走闭环留痕（已闭环的同样留痕，不能改不能删）；
   - **出库闸**：库间只要还有未闭环的异常巡检，该库间「在库」批次一律不得出库，出库口当场拦住；库间软删后不许开新巡检，但开着的异常单照样封库，直到闭环；
   - 两人同时闭环同一张异常单，行级锁串行，只许落成一次，后点的那下看到「已闭环」。
5. **化霜占窗 defrost_window**：挂在未软删库间上，写清开始时刻、结束时刻、占窗事由。
   - 质检时长下限：冷冻 ≥ 40 分钟、冷藏 ≥ 20 分钟，短了当场驳回；
   - 同一库间重叠时段只留一扇，后开的当场驳回（边界相接不算重叠）；
   - 占窗进行中，该库间剩余可收箱数按 0（货位空位/容量数字都不能绕过）。
6. **入库预占 reservation**：库间 + 货品 + 箱数（>0）+ 计划入库日；状态机 待确认→已确认→已核销。
   - 剩余口径：`容量 − 已在库箱数 − 仍待入箱数 − 已确认未核销预占箱数`；进行中化霜直接 0，不能确认；
   - 只有「已确认」预占能顶一笔待入批次，且库间/货品名/箱数必须完全对得上，入库即核销，不能一顶二；
   - 占窗结束、预占未核销不会偷偷恢复可入库，值班须重开新预占；
   - 库间软删后不能再开新占窗/新预占，但已确认未核销的预占仍可见。

## 剩余可收箱数（质检口径）
`GET /api/cells/capacity/overview`、`GET /api/cells/{id}/capacity` 返回每个库间的
容量、在库、待入、已预占、是否化霜中、剩余箱数，是全系统唯一的容量计算入口。

## 页面交互范式
- 库间：树形（库间父 / 货位子，左树右详情）
- 入库批次：状态卡片（色标 + 入/出按钮切状态）
- 巡检：打卡清单（逐条打勾 + 进度条，异常行红；点货位、填实测温度，越界行即时提示；异常单挂「未闭环」红签直到闭环）
- 货位：占用热力（按库间分组的货位格，颜色表占用状态）

## 端口
| 服务 | 端口 |
| --- | --- |
| frontend | 8244 |
| backend  | 8344 |
| mysql    | 3544 |
| redis    | 6544 |

## 运行
```bash
./start.sh
```
前端 http://127.0.0.1:8244/ ，后端接口 http://127.0.0.1:8344/api/...

## 接口
- `GET/POST /api/cells`、`GET/PUT/DELETE /api/cells/{id}`
- `GET /api/cells/capacity/overview`、`GET /api/cells/{id}/capacity`
- `GET/POST /api/locations`、`GET/PUT/DELETE /api/locations/{id}`
- `GET/POST /api/batches`、`GET/PUT /api/batches/{id}`、`PUT /api/batches/{id}/stock-in`（body 可带 `{"reservationId":n}`，也可 `?reservationId=n`，不带则自动匹配）、`PUT /api/batches/{id}/stock-out`
- `GET/POST /api/defrost-windows`（`?cellId=` 过滤）、`GET/DELETE /api/defrost-windows/{id}`
- `GET/POST /api/reservations`（`?cellId=` 过滤）、`GET /api/reservations/{id}`、`PUT /api/reservations/{id}/confirm`、`DELETE /api/reservations/{id}`
- `GET/POST /api/inspections?date=`、`GET/PUT/DELETE /api/inspections/{id}`、`PUT /api/inspections/{id}/close`（body 可带 `{"closeNote":"..."}`）、`GET /api/inspections/open-abnormal`（`?cellId=` 过滤）
