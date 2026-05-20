# bluebell

MongoDB 批量写入 + `organizationId` 数据权限 + Aggregation 权限支持。

## 模块说明

| 包 | 说明 |
|----|------|
| `com.bluebell.mongo.bulk` | 通用 bulk upsert，微信四表 `WxMsgBatchWriter` |
| `com.bluebell.mongo.permission` | `@DataPermission`、`DataPermissionMongoTemplate`、Aggregation |
| `com.bluebell.mongo.model` | 示例实体与 DTO |

## 依赖

- Spring Boot 3.2.4
- spring-data-mongodb 4.x
- Java 17+

## 使用

```java
@DataPermission
public void save(List<WxMsgDTO> list) {
    dataPermissionWxMsgBatchWriter.writeBatchInTransaction(list);
}

@DataPermission
public List<Vo> stat() {
    return dataPermissionMongoTemplate
            .aggregate(aggregation, "wx_msg_main", Vo.class)
            .getMappedResults();
}
```

实现 `OrganizationPermissionService` 解析当前用户可访问的 `organizationId` 列表。

## 构建

```bash
mvn -q compile
```
