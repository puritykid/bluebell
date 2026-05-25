package com.bluebell.mongo.permission;

import com.bluebell.mongo.bulk.MongoBulkHelper;
import com.bluebell.mongo.bulk.UpsertSpec;
import com.mongodb.bulk.BulkWriteResult;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * 带数据权限的批量写入：自动拼接 organizationId 条件与插入字段。
 */
public class DataPermissionMongoBulkHelper {

    private final MongoBulkHelper delegate;

    public DataPermissionMongoBulkHelper(MongoBulkHelper delegate) {
        this.delegate = delegate;
    }

    public <S, E> BulkWriteResult bulkUpsert(
            Class<E> entityClass,
            List<S> source,
            Function<S, Query> queryFn,
            Function<S, Update> updateFn,
            boolean ordered
    ) {
        return delegate.bulkUpsert(entityClass, source, wrapQuery(queryFn), wrapUpdate(updateFn), ordered);
    }

    public <S, E> BulkWriteResult bulkInsertOnly(
            Class<E> entityClass,
            List<S> source,
            Function<S, Query> queryFn,
            Function<S, Update> insertOnlyUpdateFn,
            boolean ordered
    ) {
        return delegate.bulkInsertOnly(entityClass, source, wrapQuery(queryFn), wrapUpdate(insertOnlyUpdateFn), ordered);
    }

    public <S, E> BulkWriteResult bulkUpsertMerged(
            Class<E> entityClass,
            List<S> source,
            Function<S, String> mergeKeyFn,
            java.util.function.BinaryOperator<S> mergeFn,
            UpsertSpec<S> spec,
            boolean ordered
    ) {
        return delegate.bulkUpsertMerged(
                entityClass, source, mergeKeyFn, mergeFn,
                UpsertSpec.of(wrapQuery(spec.queryFn()), wrapUpdate(spec.updateFn())),
                ordered
        );
    }

    /**
     * 去重后批量新增：去重键与 Query/Update 均支持任意类型（与 {@link MongoBulkHelper#batchInsertOnlyDistinct} 一致）。
     */
    public <S, E, K> BulkWriteResult bulkInsertOnlyDistinct(
            Class<E> entityClass,
            Collection<S> source,
            Function<S, K> distinctKeyFn,
            Function<S, Query> queryFn,
            Function<S, Update> insertOnlyUpdateFn,
            boolean ordered
    ) {
        return delegate.batchInsertOnlyDistinct(
                entityClass,
                source,
                distinctKeyFn,
                wrapQuery(queryFn),
                wrapUpdate(insertOnlyUpdateFn),
                ordered
        );
    }

    public <S, E, K> BulkWriteResult bulkInsertOnlyDistinctByKey(
            Class<E> entityClass,
            Collection<S> source,
            Function<S, K> distinctKeyFn,
            Function<K, Query> queryByKeyFn,
            Function<K, Update> insertOnlyUpdateFn,
            boolean ordered
    ) {
        return delegate.batchInsertOnlyDistinctByKey(
                entityClass,
                source,
                distinctKeyFn,
                key -> DataPermissionCriteria.apply(queryByKeyFn.apply(key)),
                key -> wrapUpdateOne(insertOnlyUpdateFn.apply(key)),
                ordered
        );
    }

    public void executeInTransaction(Runnable... writers) {
        delegate.executeInTransaction(writers);
    }

    public MongoBulkHelper getDelegate() {
        return delegate;
    }

    private <S> Function<S, Query> wrapQuery(Function<S, Query> queryFn) {
        return item -> DataPermissionCriteria.apply(queryFn.apply(item));
    }

    private <S> Function<S, Update> wrapUpdate(Function<S, Update> updateFn) {
        return item -> wrapUpdateOne(updateFn.apply(item));
    }

    private Update wrapUpdateOne(Update update) {
        return DataPermissionCriteria.applyInsertOrg(update);
    }
}
