# bluebell-mongo

基于 **Spring Boot 3.2 + Spring Data MongoDB 4.x** 的 MongoDB 工具库：**简单批量写入**（新增/修改/upsert）、微信消息四表封装；数据权限为可选模块。

---

## 目录

- [功能概览](#功能概览)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [数据模型与表规则](#数据模型与表规则)
- [快速开始（纯批量）](#快速开始纯批量)
- [MongoBulkHelper API](#mongobulkhelper-api)
- [微信四表写入](#微信四表写入)
- [可选：组织数据权限](#可选组织数据权限)
- [可选：Aggregation 权限](#可选aggregation-权限)
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
| 简单 API | `batchInsert` / `batchInsertOnly` / `batchUpdate` / `batchUpsert`，手写 Query+Update |
| 可扩展 | `MongoBulkHelper` 供任意业务表复用 |

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

## 快速开始（纯批量）

### 1. 引入配置

```java
@Import(com.bluebell.mongo.config.MongoBulkConfig.class)
@SpringBootApplication
public class Application { }
```

### 2. 配置 MongoDB

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://user:pass@host1:27017,host2:27017/your_db?authSource=admin&replicaSet=rs0
```

### 3. 微信四表或通用批量

```java
@Autowired WxMsgBatchWriter wxMsgBatchWriter;
@Autowired MongoBulkHelper mongoBulkHelper;

wxMsgBatchWriter.writeBatchInTransaction(dtoList);
```

详见 [MongoBulkHelper API](#mongobulkhelper-api)。

---

## 快速开始（含数据权限）

### 实现组织权限（两个接口）

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
├── config/MongoBulkConfig.java
├── model/                   # 实体与 DTO
└── support/                 # SnowflakeIdGenerator、EpochTimeUtils
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

## 可选：组织数据权限

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

## 批量写入（微信）

```java
@Autowired WxMsgBatchWriter wxMsgBatchWriter;

public void saveBatch(List<WxMsgDTO> batch) {
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

## MongoBulkHelper API

注入 `MongoBulkHelper`，为每条数据提供 `Query` + `Update` 即可。

| 方法 | 含义 |
|------|------|
| `batchInsert` | **批量插入**：直接 `insert` 列表（唯一键冲突会报错） |
| `batchInsertOnly` | **批量新增**：`upsert` + 仅 `setOnInsert`，已存在忽略 |
| `batchUpdate` | **批量修改**：只 `update`，不存在跳过 |
| `batchUpsert` | **不存在插入、存在更新**：`upsert` + `set` / `setOnInsert` |
| `batchUpsertMerged` | 批内合并后再 upsert |
| `batchInsertOnlyDistinct` | 去重后新增：Query/Update **入参为实体 E**（支持 DTO→实体映射） |
| `batchInsertOnlyDistinctByKey` | 去重后新增：Query/Update 仅依赖去重键 `K` |
| `executeInTransaction` | 多表顺序执行，失败回滚 |

```java
@Autowired MongoBulkHelper bulk;

// 1. 批量新增（orderNo 已存在则忽略）
bulk.batchInsertOnly(Order.class, list,
    o -> Query.query(Criteria.where("orderNo").is(o.getOrderNo())),
    o -> new Update().setOnInsert("orderNo", o.getOrderNo()).setOnInsert("name", o.getName()),
    true);

// 2. 批量修改（只改已有）
bulk.batchUpdate(Order.class, list,
    o -> Query.query(Criteria.where("orderNo").is(o.getOrderNo())),
    o -> new Update().set("name", o.getName()),
    true);

// 3. 不存在插入、存在更新
bulk.batchUpsert(Order.class, list,
    o -> Query.query(Criteria.where("orderNo").is(o.getOrderNo())),
    o -> new Update().set("name", o.getName()).setOnInsert("orderNo", o.getOrderNo()),
    true);

// 4. 纯插入实体列表
bulk.batchInsert(Order.class, list, false);

// 5. 去重后新增（Query/Update 入参为实体）
List<WxMsgType> types = ...;
bulk.batchInsertOnlyDistinct(WxMsgType.class, types,
    WxMsgType::getType,
    e -> Query.query(Criteria.where("type").is(e.getType())),
    e -> new Update().setOnInsert("type", e.getType()).setOnInsert("createTime", e.getCreateTime()),
    false);

// 5b. 从 DTO 去重，再转实体
bulk.batchInsertOnlyDistinct(WxMsgType.class, dtoList,
    WxMsgDTO::getType,
    dto -> { WxMsgType e = new WxMsgType(); e.setType(dto.getType()); return e; },
    e -> Query.query(Criteria.where("type").is(e.getType())),
    e -> new Update().setOnInsert("type", e.getType()),
    false);
```

`ordered=true` 用于事务内；无事务可 `false` 提高吞吐。

示例：`bulk.example.WxMsgPureServiceExample`。

---

## 微信四表写入

```java
@Autowired WxMsgBatchWriter wxMsgBatchWriter;

wxMsgBatchWriter.writeBatchInTransaction(dtoList);
```

| 表 | 规则 |
|----|------|
| 主表 | `uniqueId` 不存在才插 |
| 最新消息 | 会话键 upsert；`msgTime` 只向前；未来时间不写 |
| 微信时间 | `wxId` + `lastMsgTime` 只向前 |
| 类型字典 | `type` 不存在才插 |

---

## 可选：组织数据权限

（保留原 permission 章节标题下的内容，在 ## 组织数据权限 前加可选）

## 通用说明（权限版 Bulk）

带权限时使用 `DataPermissionMongoBulkHelper`，方法名与 `MongoBulkHelper` 相同（`batch*` / `bulk*` 均可）。

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
| `MongoBulkConfig` | 事务、`MongoBulkHelper`、`WxMsgBatchWriter` Bean |
| `EpochTimeUtils` | 时间毫秒、未来时间校验 |
| `SnowflakeIdGenerator` | 最新消息表 `_id` 雪花 |

示例：`bulk.example.WxMsgPureServiceExample`、`permission.example.WxMsgServiceExample`。

---

## 许可证

与主项目保持一致。生产环境请替换 `SnowflakeIdGenerator` 与 `DefaultOrganizationPermissionService` 示例实现。
