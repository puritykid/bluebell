package com.bluebell.mongo.upsert;

import com.bluebell.mongo.bulk.BulkWriteMode;
import com.bluebell.mongo.bulk.MongoBulkHelper;
import com.mongodb.bulk.BulkWriteResult;
import org.springframework.data.mongodb.core.BulkOperations;

import java.util.List;

/**
 * 基于实体注解 + 反射的通用批量写入。
 * <ul>
 *   <li>{@link #bulkInsertByEntity} — 批量新增（不存在才插）</li>
 *   <li>{@link #bulkUpdateByEntity} — 批量修改（只 update，不存在跳过）</li>
 *   <li>{@link #bulkUpsertByEntity} — 不存在插入、存在修改（遵循 {@link UpsertStrategy}）</li>
 * </ul>
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
     * 批量新增：按 {@link UpsertKey} 查重，已存在则忽略。
     */
    public <E> BulkWriteResult bulkInsertByEntity(Class<E> entityClass, List<E> entities, boolean ordered) {
        return bulkByEntity(entityClass, entities, BulkWriteMode.INSERT_ONLY, ordered);
    }

    /**
     * 批量修改：按业务键 update，无匹配文档则不插入。
     */
    public <E> BulkWriteResult bulkUpdateByEntity(Class<E> entityClass, List<E> entities, boolean ordered) {
        return bulkByEntity(entityClass, entities, BulkWriteMode.UPDATE_ONLY, ordered);
    }

    /**
     * 批量 upsert：不存在插入、存在修改（字段策略见实体 {@link UpsertEntity}）。
     */
    public <E> BulkWriteResult bulkUpsertByEntity(Class<E> entityClass, List<E> entities, boolean ordered) {
        return bulkByEntity(entityClass, entities, BulkWriteMode.UPSERT, ordered);
    }

    /**
     * 指定 {@link BulkWriteMode} 批量写入。
     */
    public <E> BulkWriteResult bulkByEntity(
            Class<E> entityClass,
            List<E> entities,
            BulkWriteMode writeMode,
            boolean ordered
    ) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        EntityUpsertDefinition def = EntityUpsertDefinition.of(entityClass);
        BulkOperations.BulkMode mode = ordered
                ? BulkOperations.BulkMode.ORDERED
                : BulkOperations.BulkMode.UNORDERED;
        BulkOperations bulk = mongoBulkHelper.getMongoTemplate().bulkOps(mode, entityClass);

        int ops = 0;
        for (E entity : entities) {
            if (ReflectiveUpsertBuilder.shouldSkipWrite(entity, def)) {
                continue;
            }
            ReflectiveUpsertBuilder.QueryUpdate qu =
                    ReflectiveUpsertBuilder.build(entity, def, idGenerator, writeMode);
            switch (writeMode) {
                case UPDATE_ONLY -> bulk.update(qu.query(), qu.update());
                case INSERT_ONLY, UPSERT -> bulk.upsert(qu.query(), qu.update());
            }
            ops++;
        }
        if (ops == 0) {
            return null;
        }
        return bulk.execute();
    }

    /**
     * 批内按业务键合并后再 upsert。
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
