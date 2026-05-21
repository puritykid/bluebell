# bluebell-mongo

基于 **Spring Boot 3.2 + Spring Data MongoDB 4.x** 的 MongoDB 工具库：**纯批量 upsert**（不存在插入、存在更新）、注解反射构建 Query/Update、微信消息四表封装。数据权限为**可选模块**，默认不引入。

---

## 目录

- [功能概览](#功能概览)
- [环境要求](#环境要求)
- [快速开始（纯批量，无权限）](#快速开始纯批量无权限)
- [项目结构](#项目结构)
- [数据模型与表规则](#数据模型与表规则)
- [批量写入](#批量写入)
- [反射批量 Upsert](#反射批量-upsert注解驱动推荐)
- [通用 Bulk API](#通用-bulk-api)
- [性能建议](#性能建议)
- [常见问题](#常见问题)
- [类与职责索引](#类与职责索引)
- [可选：组织数据权限](#可选组织数据权限)

---

## 功能概览

| 能力 | 说明 |
|------|------|
| 批量 upsert | 替代「先 `find` 再 `insert/update`」，100 条约 0.1～0.3s（4C8G 经验值） |
| 不存在插入 / 存在更新 | `UpsertStrategy.FULL_BY_KEY` + 业务键 `@UpsertKey` |
| 雪花 `_id` | `generateIdOnInsert = true` 时插入自动生成 `Long` 型 `_id` |
| 四表同事务 | 主表、最新消息、微信更新时间、消息类型字典 |
| 注解驱动 | 实体标注策略，无需手写 `Update.set` |
| 可选数据权限 | 需要时再 `@Import(DataPermissionConfig.class)` |

---

## 环境要求

| 项 | 版本/说明 |
|----|-----------|
| JDK | 17+ |
| Spring Boot | 3.2.4（见 `pom.xml`） |
| MongoDB | 4.4+；**多文档事务**需副本集或分片集群 |
| 构建 | Maven 3.8+ |

```bash
mvn -q compile
```

---

## 快速开始（纯批量，无权限）

### 1. 引入配置（不要引入 `DataPermissionConfig`）

```java
@SpringBootApplication
@Import(com.bluebell.mongo.config.MongoUpsertConfig.class)
public class Application { }
```

或扫描包：`scanBasePackages = {"com.yourcompany", "com.bluebell.mongo"}`（同样**不要**扫描 `permission` 下的 Config，除非你明确要权限）。

### 2. 配置 MongoDB（副本集，支持事务）

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://user:pass@host1:27017,host2:27017/your_db?authSource=admin&replicaSet=rs0
```

### 3. 批量保存（不存在插入，存在更新）

```java
@Service
@RequiredArgsConstructor
public class WxMsgService {

    private final ReflectiveWxMsgBatchWriter wxMsgBatchWriter;

    /** 四表同事务；无 @DataPermission、无 organizationId 校验 */
    public void saveBatch(List<WxMsgDTO> list) {
        wxMsgBatchWriter.writeBatchInTransaction(list);
    }
}
```

### 4. 单表通用 upsert

```java
@Autowired ReflectiveMongoBulkHelper reflectiveBulk;

public void saveLatest(List<WxMsgLatest> list) {
    reflectiveBulk.bulkUpsertByEntity(WxMsgLatest.class, list, false);
}
```

实体示例（`FULL_BY_KEY` = 按业务键：不存在插入 + 雪花 `_id`，存在则字段全量 `set`）：

```java
@UpsertEntity(strategy = UpsertStrategy.FULL_BY_KEY, generateIdOnInsert = true)
public class WxMsgLatest {
    @Id private Long id;
    @UpsertKey private String wxId;
    @UpsertKey private String chatType;
    @UpsertKey private String talker;
    private String uniqueId;
    private LocalDateTime msgTime;
}
```

---

## 项目结构

```text
src/main/java/com/bluebell/mongo/
├── bulk/                    # 通用批量写入
│   ├── MongoBulkHelper.java
│   ├── MergeUtils.java
│   ├── TimeForwardCriteria.java
│   ├── UpsertSpec.java
│   └── WxMsgBatchWriter.java
├── permission/              # 数据权限 + Aggregation
│   ├── DataPermission.java
│   ├── DataPermissionAspect.java
│   ├── DataPermissionContext.java
│   ├── DataPermissionCriteria.java
│   ├── DataPermissionAggregation.java
│   ├── DataPermissionMongoTemplate.java
│   ├── DataPermissionMongoBulkHelper.java
│   ├── DataPermissionWxMsgBatchWriter.java
│   ├── DataPermissionAggregationExecutor.java
│   ├── OrganizationPermissionService.java
│   └── example/             # 参考示例（可复制改造）
├── model/                   # 实体与 DTO
    ├── upsert/                  # 注解 + 反射 bulk
    │   ├── ReflectiveMongoBulkHelper.java
    │   └── ReflectiveWxMsgBatchWriter.java
    ├── config/                  # MongoTransactionManager 等 Bean
    └── support/
    └── SnowflakeIdGenerator.java
```

---

## 数据模型与表规则

### DTO 示例：`WxMsgDTO`

| 字段 | 说明 |
|------|------|
| `organizationId` | 可选业务字段；纯批量不做权限校验 |
| `uniqueId` | 消息全局唯一 ID（消息自带） |
| `wxId` | 微信 ID |
| `chatType` | 会话类型 |
| `talker` | 对方 ID |
| `type` | 消息类型（字典） |
| `content` | 内容 |
| `msgTime` | 消息时间（用于比「新/旧」） |

### 集合与写入语义

| 集合 | 文档名 | 业务键 | 写入规则 |
|------|--------|--------|----------|
| 主表 | `wx_msg_main` | `uniqueId` | **仅插入**：已存在则忽略（`setOnInsert`） |
| 最新消息 | `wx_msg_latest` | `wxId + chatType + talker` | **不存在插入**（雪花 `_id`），**存在则全量更新**（`FULL_BY_KEY`） |
| 微信更新时间 | `wx_last_time` | `wxId` | 本批取 `max(msgTime)`，仅向前推进 `lastMsgTime` |
| 消息类型字典 | `wx_msg_type` | `type`（全局） | **仅插入**：新类型入库，已存在不改动 |

### 推荐索引

```javascript
db.wx_msg_main.createIndex({ uniqueId: 1 }, { unique: true })
db.wx_msg_main.createIndex({ organizationId: 1 })

db.wx_msg_latest.createIndex(
  { wxId: 1, chatType: 1, talker: 1 },
  { unique: true }
)

db.wx_last_time.createIndex({ wxId: 1 }, { unique: true })
db.wx_msg_type.createIndex({ type: 1 }, { unique: true })
```

---

## 批量写入

### 推荐：四表事务写入（无数据权限）

```java
@Autowired ReflectiveWxMsgBatchWriter wxMsgBatchWriter;

public void saveBatch(List<WxMsgDTO> batch) {
    // 建议每批 100～500 条
    wxMsgBatchWriter.writeBatchInTransaction(batch);
}
```

内部顺序（同一事务）：

1. `writeMain` — 主表仅插入  
2. `writeLatest` — 会话最新（批内按会话合并 + 时间只向前）  
3. `writeWxLastTimeBulk` — 按 `wxId` 更新最大时间  
4. `writeMsgTypeDict` — 全局 `type` 字典仅插入  

### 无事务（更快，无跨表原子性）

```java
ReflectiveMongoBulkHelper bulk = ...;
bulk.getMongoBulkHelper().bulkUpsert(...);  // 或拆表多次调用 bulkUpsertByEntity
```

### 不要使用（性能差）

```java
// ❌ 每条 2 次往返：findOne + save
for (WxMsgDTO m : list) {
    if (mongoTemplate.findOne(...) == null) {
        mongoTemplate.insert(...);
    }
}
```

### 事务内 bulk 模式

| 场景 | BulkMode |
|------|----------|
| 有 `@Transactional` / `executeInTransaction` | **ORDERED**（必须） |
| 无事务、追求吞吐 | **UNORDERED** |

---

## 反射批量 Upsert（注解驱动，推荐）

无需手写 `Update.set("field", ...)`，在**实体字段**上加注解，由 `ReflectiveMongoBulkHelper` 反射构建 Query/Update。

### 实体级注解 `@UpsertEntity`

| strategy | 含义 |
|----------|------|
| `INSERT_ONLY` | 按业务键：不存在插入，存在忽略（字段默认 `setOnInsert`） |
| `FULL_BY_KEY` | 按业务键：不存在插入，存在则**全量 set 更新** |
| `UPSERT_IF_NEWER` | 按业务键 + `timeField`：仅时间向前才更新 |
| `UPSERT_SELECTIVE` | 不存在：插入（`generateIdOnInsert` 可生成雪花 `_id`）；存在：仅更新 `@UpsertOnUpdate` 字段；可选 `timeField` 防旧盖新 |

```java
@UpsertEntity(strategy = UpsertStrategy.INSERT_ONLY)
public class WxMsgMain {
    @UpsertKey
    private String uniqueId;
    private String wxId;   // 默认 INSERT_ONLY
}

/** 最常见：不存在插入，存在全量更新 */
@UpsertEntity(strategy = UpsertStrategy.FULL_BY_KEY, generateIdOnInsert = true)
public class WxMsgLatest {
    @Id private Long id;
    @UpsertKey private String wxId;
    @UpsertKey private String chatType;
    @UpsertKey private String talker;
    private String uniqueId;
    private LocalDateTime msgTime;
}
```

### 存在时只更新部分字段（可选）

使用 `UPSERT_SELECTIVE` + `@UpsertOnUpdate`：未标注字段仅插入时写入。

### 字段级注解 `@UpsertField`（覆盖默认）

| mode | 说明 |
|------|------|
| `KEY` | 查询键，仅 `setOnInsert` 写入键值 |
| `INSERT_ONLY` | 仅插入 |
| `ALWAYS` | 插入/更新都 set |
| `IGNORE` | 不参与 |

```java
@UpsertField(mode = FieldUpsertMode.IGNORE)
private String tempField;
```

也可用 `@UpsertEntity(keys = {"orderNo"})` 代替多个 `@UpsertKey`。

### 调用

```java
@Autowired ReflectiveMongoBulkHelper reflectiveBulk;
@Autowired ReflectiveWxMsgBatchWriter reflectiveWxWriter;

public void saveOrders(List<Order> orders) {
    reflectiveBulk.bulkUpsertByEntity(Order.class, orders, true);
}

public void saveWx(List<WxMsgDTO> list) {
    reflectiveWxWriter.writeBatchInTransaction(list);
}
```

新增实体：**`@UpsertEntity` + `@UpsertKey` → `bulkUpsertByEntity`**，无需数据权限注解。

---

## 通用 Bulk API

适用于非微信业务表（手写 Query/Update 的底层 API）。

```java
@Autowired MongoBulkHelper bulkHelper;

bulkHelper.bulkUpsert(
        Order.class,
        list,
        o -> Query.query(Criteria.where("orderNo").is(o.getOrderNo())),
        o -> new Update().set("name", o.getName()).setOnInsert("orderNo", o.getOrderNo()),
        true
);
```

| 方法 | 用途 |
|------|------|
| `bulkUpsert` | 存在更新，不存在插入 |
| `bulkInsertOnly` | 仅 `setOnInsert`，存在忽略 |
| `bulkUpsertMerged` | 批内按键合并后再 upsert |
| `bulkInsertOnlyDistinct` | 去重后字典式插入 |
| `executeInTransaction` | 多表顺序执行，失败回滚 |

默认使用 `MongoBulkHelper` / `ReflectiveMongoBulkHelper`（`com.bluebell.mongo.bulk` / `upsert`）。

---

## 性能建议

| 场景 | 建议 |
|------|------|
| 批量大小 | 单事务 **100～500** 条；过万条拆多事务 |
| 消费 lag | Kafka `max.poll.records` 与 Mongo 批大小协调；处理成功再 `ack` |
| 多线程 | 同一 `wxId+chatType+talker` 路由到同一线程，避免 latest 乱序 |
| 连接池 | 4C 机器 `maxPoolSize` 约 20～30，不宜过大 |
| 索引 | 写入前建好；事务中不建索引 |
| 写入 | 优先 bulk upsert，避免循环 `findOne` |

---

## 常见问题

**Q：事务报错 `Transaction numbers are only allowed on a replica set`？**  
A：MongoDB 需为副本集；连接串带 `replicaSet=xxx`；已注册 `MongoTransactionManager`（见 `MongoBulkConfig`）。

**Q：`@DataPermission` 不生效？**  
A：确认方法在 Spring Bean 上、通过代理调用（避免同类 `this.xxx()`）；AOP 依赖 `spring-boot-starter-aop`。

**Q：Aggregation 没有按组织过滤？**  
A：使用 `DataPermissionMongoTemplate`，或 `DataPermissionAggregation.prependOrgMatch(agg)`。

**Q：最新消息被旧数据覆盖？**  
A：确认 `msgTime` 正确；latest 表已用 `TimeForwardCriteria.timeForward`；同批已 `MergeUtils.mergeByKey`。

**Q：业务传了机构 ID 但仍查到别的机构数据？**  
A：确认方法参数有 `@BizOrgId` 或 `bizOrgParam`；实现 `OrganizationHierarchyService`；编译开启 `-parameters`。

**Q：如何接入现有工程（只要批量）？**  
A：`@Import(MongoUpsertConfig.class)` → 注入 `ReflectiveWxMsgBatchWriter` → `writeBatchInTransaction`，不要引入 `DataPermissionConfig`。

---

## 类与职责索引

| 类 | 职责 |
|----|------|
| `MongoUpsertConfig` | **默认** Bean：事务、`ReflectiveMongoBulkHelper`、`ReflectiveWxMsgBatchWriter` |
| `MongoBulkHelper` | 通用 bulk upsert / 事务包装 |
| `ReflectiveWxMsgBatchWriter` | 微信四表反射批量写（**无权限**） |
| `ReflectiveMongoBulkHelper` | 注解 + 反射 `bulkUpsertByEntity` |
| `WxMsgBatchWriter` | 微信四表手写版（已 `@Deprecated` 配置） |
| `DataPermissionWxMsgBatchWriter` | 可选：微信四表 + `organizationId` 校验 |
| `DataPermission` | 权限注解 |
| `DataPermissionAspect` | 注解 AOP |
| `OrganizationPermissionService` | **业务实现**用户组织 ID 列表 |
| `OrganizationHierarchyService` | **业务实现**机构子树查询 |
| `DataPermissionResolver` | 用户权限 ∩ 业务子树 |
| `BizOrgId` | 标注业务机构参数 |
| `DataPermissionCriteria` | Query/Update 拼条件 |
| `DataPermissionAggregation` | Aggregation prepend `$match` |
| `DataPermissionMongoTemplate` | find/aggregate 自动权限 |
| `DataPermissionMongoBulkHelper` | bulk 自动权限 |
| `TimeForwardCriteria` | 时间只向前查询条件 |
| `MergeUtils` | 批内按键合并 |
| `ReflectiveMongoBulkHelper` | 注解 + 反射通用 bulk |
| `@UpsertEntity` / `@UpsertKey` | 实体 upsert 策略与业务键 |
| `ReflectiveWxMsgBatchWriter` | 微信四表反射批量写 |
| `MongoBulkConfig` | 已废弃，请用 `MongoUpsertConfig` |
| `SnowflakeIdGenerator` | 插入时雪花 `_id` |

示例：`upsert.example.WxMsgPureServiceExample`（纯批量）。

---

## 可选：组织数据权限

仅当业务需要按 `organizationId` 过滤查询/写入时，**额外** `@Import(DataPermissionConfig.class)`，并实现：

| 接口 | 职责 |
|------|------|
| `OrganizationPermissionService` | 当前用户可访问机构 ID |
| `OrganizationHierarchyService` | 业务机构 + 子机构 ID |

Service 方法加 `@DataPermission`，写入用 `DataPermissionWxMsgBatchWriter`，查询用 `DataPermissionMongoTemplate`。  
详见 `permission.example` 包。批量 upsert **不依赖**该模块。

---

## 许可证

与主项目保持一致。生产环境请替换 `SnowflakeIdGenerator` 与 `DefaultOrganizationPermissionService` 示例实现。
