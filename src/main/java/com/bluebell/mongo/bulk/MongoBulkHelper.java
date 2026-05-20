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
 * MongoDB 批量 upsert 通用工具：避免「先查再写」，统一 bulk + 可选事务。
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
    public <S, E> BulkWriteResult bulkUpsert(
            Class<E> entityClass,
            List<S> source,
            UpsertSpec<S> spec,
            boolean ordered
    ) {
        return bulkUpsert(entityClass, source, spec.queryFn(), spec.updateFn(), ordered);
    }

    public <S, E> BulkWriteResult bulkUpsert(
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

    /**
     * 仅插入：已存在则忽略（Update 只含 setOnInsert，不要 set）。
     */
    public <S, E> BulkWriteResult bulkInsertOnly(
            Class<E> entityClass,
            List<S> source,
            Function<S, Query> queryFn,
            Function<S, Update> insertOnlyUpdateFn,
            boolean ordered
    ) {
        return bulkUpsert(entityClass, source, queryFn, insertOnlyUpdateFn, ordered);
    }

    /**
     * 先按键合并，再 bulk upsert。
     */
    public <S, E> BulkWriteResult bulkUpsertMerged(
            Class<E> entityClass,
            List<S> source,
            Function<S, String> mergeKeyFn,
            java.util.function.BinaryOperator<S> mergeFn,
            UpsertSpec<S> spec,
            boolean ordered
    ) {
        List<S> merged = MergeUtils.mergeByKey(source, mergeKeyFn, mergeFn);
        return bulkUpsert(entityClass, merged, spec, ordered);
    }

    /**
     * 对集合去重后做「没有才插入」字典写入。
     */
    public <S, E> BulkWriteResult bulkInsertOnlyDistinct(
            Class<E> entityClass,
            Collection<S> source,
            Function<S, String> distinctKeyFn,
            Function<String, Query> queryByKeyFn,
            Function<String, Update> insertOnlyUpdateFn,
            boolean ordered
    ) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        List<String> keys = source.stream().map(distinctKeyFn).distinct().toList();
        BulkOperations.BulkMode mode = ordered
                ? BulkOperations.BulkMode.ORDERED
                : BulkOperations.BulkMode.UNORDERED;
        BulkOperations bulk = mongoTemplate.bulkOps(mode, entityClass);
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
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
}
