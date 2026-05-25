# bluebell-mongo

基于 **Spring Boot 3.2 + Spring Data MongoDB 4.x** 的 MongoDB 工具库：批量写入（bulk upsert）、组织数据权限（`organizationId`）、Aggregation 管道权限、微信消息四表业务封装。

---

## 目录

- [功能概览](#功能概览)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [数据模型与表规则](#数据模型与表规则)
- [组织数据权限](#组织数据权限)
- [批量写入](#批量写入)
- [Aggregation 使用](#aggregation-使用)
- [通用 Bulk API](#通用-bulk-api)
- [性能建议](#性能建议)
- [常见问题](#常见问题)
- [类与职责索引](#类与职责索引)

---

## 功能概览

| 能力 | 说明 |
|------|------|
| 批量 upsert | 替代「先 `find` 再 `insert/update`」，减少网络往返，100 条由约 2s 降至约 0.1～0.3s（4C8G 经验值） |
| 四表同事务 | 主表、最新消息、微信更新时间、消息类型字典一次事务写入 |
| 幂等与防旧盖新 | `uniqueId` 去重；`msgTime` 只向前更新，避免乱序覆盖 |
| `@DataPermission` | 用户权限 ∩ 业务机构子树，自动拼接 `organizationId in (...)` |
| Aggregation 权限 | 管道最前自动插入 `$match`；支持 Spring DSL 与原生 `Document` 管道 |
| 可扩展 | `MongoBulkHelper` 可供非微信业务复用 |

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

## 快速开始

### 1. 扫描组件

```java
@SpringBootApplication(scanBasePackages = {"com.yourcompany", "com.bluebell.mongo"})
public class Application { }
```

### 2. 配置 MongoDB

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://user:pass@host1:27017,host2:27017/your_db?authSource=admin&replicaSet=rs0
```

### 3. 实现组织权限（必做，两个接口）

```java
/** ① 当前用户数据权限范围内的机构 ID */
@Service
public class YourOrganizationPermissionService implements OrganizationPermissionService {
    @Override
    public List<String> resolveOrganizationIds() {
        return loginUser.getOrganizationIds();
        // 超管：return Collections.emptyList();
    }
}

/** ② 业务机构 + 所有子机构 */
@Service
public class YourOrganizationHierarchyService implements OrganizationHierarchyService {
    @Override
    public List<String> resolveSelfAndChildren(String organizationId) {
        return orgRepository.findSelfAndDescendantIds(organizationId);
    }
}
```

### 4. 批量保存消息

```java
@Service
@RequiredArgsConstructor
public class WxMsgService {

    private final DataPermissionWxMsgBatchWriter batchWriter;

    @DataPermission
    public void saveBatch(List<WxMsgDTO> list) {
        batchWriter.writeBatchInTransaction(list);
    }
}
```

### 5. 聚合统计（自动带权限）

```java
@Service
@RequiredArgsConstructor
public class WxStatService {

    private final DataPermissionMongoTemplate mongoTemplate;

    @DataPermission
    public List<WxStatVO> statByWx() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("deleted").is(false)),
                Aggregation.group("wxId").count().as("cnt")
        );
        return mongoTemplate.aggregate(agg, "wx_msg_main", WxStatVO.class).getMappedResults();
    }
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
| `organizationId` | 组织 ID，权限与入库必填 |
| `uniqueId` | 消息全局唯一 ID（消息自带） |
| `wxId` | 微信 ID |
| `chatType` | 会话类型 |
| `talker` | 对方 ID |
| `type` | 消息类型（字典） |
| `content` | 内容 |
| `msgTime` | 消息时间，**epoch 毫秒（long）**；`null` 可写；**大于当前时间不 insert/update** |

### 集合与写入语义

| 集合 | 文档名 | 业务键 | 写入规则 |
|------|--------|--------|----------|
| 主表 | `wx_msg_main` | `uniqueId` | **仅插入**：已存在则忽略（`setOnInsert`） |
| 最新消息 | `wx_msg_latest` | `wxId + chatType + talker` | 按业务键 upsert；`msgTime` 为 null 仍写；未来时间跳过；存在时仅 `msgTime` ≥ 库中才更新 |
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

## 组织数据权限

### 核心公式

```text
最终机构范围 = 用户数据权限机构
              ∩
              （业务传入机构 + 其所有子机构）   ← 业务未传机构时，仅按用户权限
```

由 `DataPermissionResolver` 计算，结果写入 `DataPermissionContext`，Mongo 条件为：

```javascript
{ organizationId: { $in: [最终机构ID...] } }
```

### 两个必实现接口

| 接口 | 职责 |
|------|------|
| `OrganizationPermissionService` | 当前登录用户可访问的机构 ID 列表 |
| `OrganizationHierarchyService` | 给定业务机构 ID，返回 **自身 + 全部子机构** ID |

### 注解与业务机构入参

```java
// 方式1：@BizOrgId 标注参数（推荐）
@DataPermission
public List<Vo> query(@BizOrgId String organizationId, String wxId) { ... }

// 方式2：按参数名（pom 已开启 -parameters）
@DataPermission(bizOrgParam = "organizationId")
public List<Vo> query(String organizationId) { ... }

// 仅用户权限，不传业务机构
@DataPermission
public List<Vo> query() { ... }
```

| 注解属性 | 说明 |
|----------|------|
| `field` | 文档字段名，默认 `organizationId` |
| `bizOrgParam` | 业务机构参数名 |
| `includeBizChildren` | 是否展开子机构，默认 `true` |
| `allowAllWhenEmpty` | 用户权限为空是否视为超管，默认 `true` |

### 决策表（最终 Mongo 过滤）

| 用户权限 | 业务机构参数 | 结果 |
|----------|--------------|------|
| 超管（空 + allowAll） | 未传 | **不过滤** |
| 超管 | 传入 `org_A` | 仅 `org_A` 子树：`in (A, A1, A2...)` |
| 普通用户 `[A,B]` | 未传 | `in (A, B)` |
| 普通用户 `[A,B]` | 传入 `A`（子树 A,A1） | `in (A, A1)`（交集） |
| 普通用户 `[A,B]` | 传入 `C` | **无权限**（交集为空，查不到数据） |

### 生效范围

| 操作类型 | 实现方式 |
|----------|----------|
| `find` / `count` / `remove` | `DataPermissionMongoTemplate` |
| `aggregate` | `DataPermissionAggregation.prependOrgMatch` 或上述 Template |
| bulk 写入 | `DataPermissionMongoBulkHelper` |

### 写入校验

批量写入前会 `assertWritable(dto.getOrganizationId())`，`organizationId` 必须在**最终交集**内。

---

## 批量写入

### 推荐：四表事务写入

```java
@DataPermission
public void saveBatch(List<WxMsgDTO> batch) {
    // 建议每批 100～500 条，避免单事务过大
    batchWriter.writeBatchInTransaction(batch);
}
```

内部顺序（同一事务）：

1. `writeMain` — 主表仅插入  
2. `writeLatest` — 会话最新（批内按会话合并 + 时间只向前）  
3. `writeWxLastTimeBulk` — 按 `wxId` 更新最大时间  
4. `writeMsgTypeDict` — 全局 `type` 字典仅插入  

### 无事务（更快，无跨表原子性）

```java
@Autowired
WxMsgBatchWriter wxMsgBatchWriter;  // 无权限版

wxMsgBatchWriter.writeBatchWithoutTransaction(batch);
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

## Aggregation 使用

### 方式一：注入 `DataPermissionMongoTemplate`（推荐）

```java
@DataPermission
public List<Vo> stat() {
    Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("status").is(1)),
            Aggregation.group("wxId").count().as("cnt")
    );
    return mongoTemplate.aggregate(agg, "wx_msg_main", Vo.class).getMappedResults();
}
```

等价于在管道最前增加：

```json
{ "$match": { "organizationId": { "$in": ["org_001", "org_002"] } } }
```

### 方式二：手动 prepend

```java
Aggregation withPerm = DataPermissionAggregation.prependOrgMatch(rawAgg);
mongoTemplate.aggregate(withPerm, "wx_msg_main", Vo.class);
```

### 方式三：构建时合并条件（避免双重 $match）

仅在使用**普通** `MongoTemplate` 时：

```java
Criteria c = DataPermissionAggregation.andOrg(Criteria.where("status").is(1));
Aggregation agg = Aggregation.newAggregation(Aggregation.match(c), ...);
```

若已使用 `DataPermissionMongoTemplate`，不要再 `andOrg`，否则会重复过滤。

### 方式四：原生 Document 管道

```java
List<Document> pipeline = List.of(
        new Document("$match", new Document("status", 1)),
        new Document("$group", ...)
);
List<Document> result = new DataPermissionAggregationExecutor(mongoTemplate)
        .aggregateDocuments("wx_msg_main", pipeline);
```

### `$lookup` 子管道

主集合会自动加权限；**关联集合**需在 `lookup.pipeline` 中自行添加：

```java
Aggregation.lookup()
    .from("wx_msg_latest")
    .localField("wxId")
    .foreignField("wxId")
    .pipeline(
            Aggregation.match(DataPermissionCriteria.orgCriteria()),
            Aggregation.limit(1)
    )
    .as("latest");
```

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
| `rejectFutureTime` | 实体注解属性：`timeField` &gt; 当前时间不 insert/update；`timeField` 为 null 仍按业务键 upsert |

```java
@UpsertEntity(strategy = UpsertStrategy.INSERT_ONLY)
public class WxMsgMain {
    @UpsertKey
    private String uniqueId;
    private String wxId;   // 默认 INSERT_ONLY
}

/** 不存在插入 + 雪花 id；存在只更新 @UpsertOnUpdate；msgTime 更小不更新 */
@UpsertEntity(
    strategy = UpsertStrategy.UPSERT_SELECTIVE,
    timeField = "msgTime",
    generateIdOnInsert = true
)
public class WxMsgLatest {
    @Id private Long id;
    @UpsertKey private String wxId;
    @UpsertKey private String chatType;
    @UpsertKey private String talker;
    @UpsertOnUpdate private String uniqueId;
    @UpsertOnUpdate private Long msgTime;      // epoch 毫秒
    private Long createTime;
}
```

### 存在时选择性更新 `@UpsertOnUpdate`

配合 `UPSERT_SELECTIVE`：未标注字段仅在**不存在插入**时写入；标注字段在**存在更新**时 `set`。

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

@DataPermission
public void saveOrders(@BizOrgId String orgId, List<Order> orders) {
    reflectiveBulk.bulkUpsertByEntity(Order.class, orders, true);
}

// 微信四表（已封装）
@Autowired ReflectiveWxMsgBatchWriter reflectiveWxWriter;

@DataPermission
public void save(@BizOrgId String organizationId, List<WxMsgDTO> list) {
    reflectiveWxWriter.writeBatchInTransaction(list);
}
```

新增实体只需：**加注解 → `bulkUpsertByEntity`**，与数据权限、`@DataPermission` 自动兼容。

---

## 通用 Bulk API

适用于非微信业务表（手写 Query/Update 的底层 API）。

```java
@Autowired
DataPermissionMongoBulkHelper bulkHelper;

@DataPermission
public void saveOrders(List<OrderDTO> list) {
    bulkHelper.bulkInsertOnly(
            Order.class,
            list,
            o -> Query.query(Criteria.where("orderNo").is(o.getOrderNo())),
            o -> new Update()
                    .setOnInsert("orderNo", o.getOrderNo())
                    .setOnInsert("organizationId", o.getOrganizationId()),
            true  // 事务内 true
    );
}
```

| 方法 | 用途 |
|------|------|
| `bulkUpsert` | 存在更新，不存在插入 |
| `bulkUpdate` | 仅 update，不存在不插入 |
| `bulkInsertOnly` | 仅 `setOnInsert`，存在忽略 |
| `BulkWriteMode` | 反射工具三种模式枚举 |
| `bulkUpsertMerged` | 批内按键合并后再 upsert |
| `bulkInsertOnlyDistinct` | 去重后字典式插入 |
| `executeInTransaction` | 多表顺序执行，失败回滚 |

无权限场景使用 `MongoBulkHelper`（`com.bluebell.mongo.bulk`）。

---

## 性能建议

| 场景 | 建议 |
|------|------|
| 批量大小 | 单事务 **100～500** 条；过万条拆多事务 |
| 消费 lag | Kafka `max.poll.records` 与 Mongo 批大小协调；处理成功再 `ack` |
| 多线程 | 同一 `wxId+chatType+talker` 路由到同一线程，避免 latest 乱序 |
| 连接池 | 4C 机器 `maxPoolSize` 约 20～30，不宜过大 |
| 索引 | 写入前建好；事务中不建索引 |
| 权限 | 优先 bulk + `@DataPermission`，避免循环 `findOne` |

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

**Q：如何接入现有工程？**  
A：实现 `OrganizationPermissionService` + `OrganizationHierarchyService` → Service 方法加 `@DataPermission` 与 `@BizOrgId` → 使用 `DataPermissionMongoTemplate` / `DataPermissionWxMsgBatchWriter`。

---

## 类与职责索引

| 类 | 职责 |
|----|------|
| `MongoBulkHelper` | 通用 bulk upsert / 事务包装 |
| `WxMsgBatchWriter` | 微信四表批量写（无权限版） |
| `DataPermissionWxMsgBatchWriter` | 微信四表 + `organizationId` |
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
| `MongoBulkConfig` | 事务管理器、Writer Bean |
| `SnowflakeIdGenerator` | 最新消息表 `_id` 雪花 |

示例代码：`permission.example.WxMsgServiceExample`、`AggregationServiceExample`。

---

## 许可证

与主项目保持一致。生产环境请替换 `SnowflakeIdGenerator` 与 `DefaultOrganizationPermissionService` 示例实现。
