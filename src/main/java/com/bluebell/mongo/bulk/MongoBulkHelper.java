package com.bluebell.mongo.bulk;

import com.mongodb.bulk.BulkWriteResult;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * MongoDB 批量写入通用工具：避免「先查再写」，统一 bulk + 可选事务。
 * <ul>
 *   <li>{@link #batchInsert} — 批量插入（重复键可能报错）</li>
 *   <li>{@link #batchInsertOnly} — 批量新增：不存在才插，已存在忽略</li>
 *   <li>{@link #batchUpdate} — 批量修改：只 update，不存在跳过</li>
 *   <li>{@link #batchUpsert} — 不存在插入、存在更新</li>
 * </ul>
 */
public class MongoBulkHelper {

    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate transactionTemplate;

    public MongoBulkHelper(MongoTemplate mongoTemplate) {
        this(mongoTemplate, null);
    }

    public MongoBulkHelper(MongoTemplate mongoTemplate, TransactionTemplate transactionTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 在事务中顺序执行多组 bulk（任一步失败整组回滚）。
     * 事务内请使用 {@link BulkOperations.BulkMode#ORDERED}。
     */
    public void executeInTransaction(Runnable... writers) {
        if (transactionTemplate == null) {
            throw new IllegalStateException("未配置 TransactionTemplate，无法执行事务批量写");
        }
        transactionTemplate.executeWithoutResult(status -> {
            try {
                for (Runnable writer : writers) {
                    writer.run();
                }
            } catch (Exception ex) {
                status.setRollbackOnly();
                throw ex;
            }
        });
    }

    /**
     * 通用 bulk upsert。
     *
     * @param entityClass  实体类型
     * @param source       源数据列表
     * @param spec         查询与 Update 构造
     * @param ordered      是否有序（事务内必须 true）
     */
    /**
     * 批量插入实体列表（纯 insert；主键/唯一键冲突由 MongoDB 报错）。
     */
    public <E> BulkWriteResult batchInsert(Class<E> entityClass, List<E> entities, boolean ordered) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        BulkOperations.BulkMode mode = ordered
                ? BulkOperations.BulkMode.ORDERED
                : BulkOperations.BulkMode.UNORDERED;
        BulkOperations bulk = mongoTemplate.bulkOps(mode, entityClass);
        bulk.insert(entities);
        return bulk.execute();
    }

    public <S, E> BulkWriteResult batchUpsert(
            Class<E> entityClass,
            List<S> source,
            UpsertSpec<S> spec,
            boolean ordered
    ) {
        return batchUpsert(entityClass, source, spec.queryFn(), spec.updateFn(), ordered);
    }

    public <S, E> BulkWriteResult batchUpsert(
            Class<E> entityClass,
            List<S> source,
            Function<S, Query> queryFn,
            Function<S, Update> updateFn,
            boolean ordered
    ) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        BulkOperations.BulkMode mode = ordered
                ? BulkOperations.BulkMode.ORDERED
                : BulkOperations.BulkMode.UNORDERED;
        BulkOperations bulk = mongoTemplate.bulkOps(mode, entityClass);
        for (S item : source) {
            bulk.upsert(queryFn.apply(item), updateFn.apply(item));
        }
        return bulk.execute();
    }

    /** 批量修改：仅更新已存在文档，无匹配则不插入。 */
    public <S, E> BulkWriteResult batchUpdate(
            Class<E> entityClass,
            List<S> source,
            Function<S, Query> queryFn,
            Function<S, Update> updateFn,
            boolean ordered
    ) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        BulkOperations.BulkMode mode = ordered
                ? BulkOperations.BulkMode.ORDERED
                : BulkOperations.BulkMode.UNORDERED;
        BulkOperations bulk = mongoTemplate.bulkOps(mode, entityClass);
        for (S item : source) {
            bulk.update(queryFn.apply(item), updateFn.apply(item));
        }
        return bulk.execute();
    }

    /**
     * 批量新增：按 query 匹配，不存在才插入（Update 仅 setOnInsert）。
     */
    public <S, E> BulkWriteResult batchInsertOnly(
            Class<E> entityClass,
            List<S> source,
            Function<S, Query> queryFn,
            Function<S, Update> insertOnlyUpdateFn,
            boolean ordered
    ) {
        return batchUpsert(entityClass, source, queryFn, insertOnlyUpdateFn, ordered);
    }

    /** 先按键合并，再 batchUpsert。 */
    public <S, E> BulkWriteResult batchUpsertMerged(
            Class<E> entityClass,
            List<S> source,
            Function<S, String> mergeKeyFn,
            java.util.function.BinaryOperator<S> mergeFn,
            UpsertSpec<S> spec,
            boolean ordered
    ) {
        List<S> merged = MergeUtils.mergeByKey(source, mergeKeyFn, mergeFn);
        return batchUpsert(entityClass, merged, spec, ordered);
    }

    /**
     * 去重后批量新增（推荐）：按 {@code distinctKeyFn} 去重，Query/Update 由整条源数据 {@code S} 构建，
     * 便于组合键（如 wxId+type）或 type 为 Integer/String 等任意类型。
     */
    public <S, E, K> BulkWriteResult batchInsertOnlyDistinct(
            Class<E> entityClass,
            Collection<S> source,
            Function<S, K> distinctKeyFn,
            Function<S, Query> queryFn,
            Function<S, Update> insertOnlyUpdateFn,
            boolean ordered
    ) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        java.util.Map<K, S> deduped = MergeUtils.dedupeByKey(source, distinctKeyFn);
        if (deduped.isEmpty()) {
            return null;
        }
        BulkOperations.BulkMode mode = ordered
                ? BulkOperations.BulkMode.ORDERED
                : BulkOperations.BulkMode.UNORDERED;
        BulkOperations bulk = mongoTemplate.bulkOps(mode, entityClass);
        for (S item : deduped.values()) {
            bulk.upsert(queryFn.apply(item), insertOnlyUpdateFn.apply(item));
        }
        return bulk.execute();
    }

    /**
     * 去重后批量新增：Query/Update 仅依赖去重键 {@code K}（单字段字典，如 type 为 Integer 或 String）。
     */
    public <S, E, K> BulkWriteResult batchInsertOnlyDistinctByKey(
            Class<E> entityClass,
            Collection<S> source,
            Function<S, K> distinctKeyFn,
            Function<K, Query> queryByKeyFn,
            Function<K, Update> insertOnlyUpdateFn,
            boolean ordered
    ) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        java.util.Map<K, S> deduped = MergeUtils.dedupeByKey(source, distinctKeyFn);
        if (deduped.isEmpty()) {
            return null;
        }
        BulkOperations.BulkMode mode = ordered
                ? BulkOperations.BulkMode.ORDERED
                : BulkOperations.BulkMode.UNORDERED;
        BulkOperations bulk = mongoTemplate.bulkOps(mode, entityClass);
        for (java.util.Map.Entry<K, S> entry : deduped.entrySet()) {
            K key = entry.getKey();
            bulk.upsert(queryByKeyFn.apply(key), insertOnlyUpdateFn.apply(key));
        }
        return bulk.execute();
    }

    public MongoTemplate getMongoTemplate() {
        return mongoTemplate;
    }

    public TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
    }

    /**
     * 计时执行，便于对比「先查再写」与 bulk 耗时。
     */
    public static long measureMs(Supplier<?> action) {
        long t0 = System.nanoTime();
        action.get();
        return (System.nanoTime() - t0) / 1_000_000;
    }

    // —— 旧方法名兼容 ——

    public <S, E> BulkWriteResult bulkUpsert(
            Class<E> entityClass, List<S> source, UpsertSpec<S> spec, boolean ordered) {
        return batchUpsert(entityClass, source, spec, ordered);
    }

    public <S, E> BulkWriteResult bulkUpsert(
            Class<E> entityClass, List<S> source, Function<S, Query> queryFn,
            Function<S, Update> updateFn, boolean ordered) {
        return batchUpsert(entityClass, source, queryFn, updateFn, ordered);
    }

    public <S, E> BulkWriteResult bulkUpdate(
            Class<E> entityClass, List<S> source, Function<S, Query> queryFn,
            Function<S, Update> updateFn, boolean ordered) {
        return batchUpdate(entityClass, source, queryFn, updateFn, ordered);
    }

    public <S, E> BulkWriteResult bulkInsertOnly(
            Class<E> entityClass, List<S> source, Function<S, Query> queryFn,
            Function<S, Update> insertOnlyUpdateFn, boolean ordered) {
        return batchInsertOnly(entityClass, source, queryFn, insertOnlyUpdateFn, ordered);
    }

    public <S, E> BulkWriteResult bulkUpsertMerged(
            Class<E> entityClass, List<S> source, Function<S, String> mergeKeyFn,
            java.util.function.BinaryOperator<S> mergeFn, UpsertSpec<S> spec, boolean ordered) {
        return batchUpsertMerged(entityClass, source, mergeKeyFn, mergeFn, spec, ordered);
    }

    /** @deprecated 请用 {@link #batchInsertOnlyDistinct}（按源对象 S）或 {@link #batchInsertOnlyDistinctByKey} */
    @Deprecated
    public <S, E, K> BulkWriteResult bulkInsertOnlyDistinct(
            Class<E> entityClass, Collection<S> source, Function<S, K> distinctKeyFn,
            Function<K, Query> queryByKeyFn, Function<K, Update> insertOnlyUpdateFn, boolean ordered) {
        return batchInsertOnlyDistinctByKey(entityClass, source, distinctKeyFn, queryByKeyFn, insertOnlyUpdateFn, ordered);
    }
}
