package com.bluebell.mongo.upsert;

import com.bluebell.mongo.bulk.MongoBulkHelper;
import com.mongodb.bulk.BulkWriteResult;
import org.springframework.data.mongodb.core.BulkOperations;

import java.util.List;

/**
 * 基于实体注解 + 反射的通用批量 upsert。
 */
public class ReflectiveMongoBulkHelper {

    private final MongoBulkHelper mongoBulkHelper;
    private final UpsertIdGenerator idGenerator;

    public ReflectiveMongoBulkHelper(MongoBulkHelper mongoBulkHelper) {
        this(mongoBulkHelper, null);
    }

    public ReflectiveMongoBulkHelper(MongoBulkHelper mongoBulkHelper, UpsertIdGenerator idGenerator) {
        this.mongoBulkHelper = mongoBulkHelper;
        this.idGenerator = idGenerator;
    }

    /**
     * 按实体 {@link UpsertEntity} 注解批量 upsert。
     */
    public <E> BulkWriteResult bulkUpsertByEntity(Class<E> entityClass, List<E> entities, boolean ordered) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        EntityUpsertDefinition def = EntityUpsertDefinition.of(entityClass);
        BulkOperations.BulkMode mode = ordered
                ? BulkOperations.BulkMode.ORDERED
                : BulkOperations.BulkMode.UNORDERED;
        BulkOperations bulk = mongoBulkHelper.getMongoTemplate().bulkOps(mode, entityClass);

        for (E entity : entities) {
            if (ReflectiveUpsertBuilder.shouldSkipWrite(entity, def)) {
                continue;
            }
            ReflectiveUpsertBuilder.QueryUpdate qu = ReflectiveUpsertBuilder.build(entity, def, idGenerator);
            bulk.upsert(qu.query(), qu.update());
        }
        return bulk.execute();
    }

    /**
     * 批内按业务键合并（保留时间最大等）后再 upsert。
     */
    public <E> BulkWriteResult bulkUpsertMergedByEntity(
            Class<E> entityClass,
            List<E> entities,
            java.util.function.Function<E, String> mergeKeyFn,
            java.util.function.BinaryOperator<E> mergeFn,
            boolean ordered
    ) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        java.util.Map<String, E> merged = new java.util.LinkedHashMap<>();
        for (E e : entities) {
            merged.merge(mergeKeyFn.apply(e), e, mergeFn);
        }
        return bulkUpsertByEntity(entityClass, List.copyOf(merged.values()), ordered);
    }

    public MongoBulkHelper getMongoBulkHelper() {
        return mongoBulkHelper;
    }
}
