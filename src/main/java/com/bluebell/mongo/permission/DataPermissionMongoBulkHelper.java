package com.bluebell.mongo.permission;

import com.bluebell.mongo.bulk.InsertOnlyDistinctSpec;
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

    /** 实体列表去重插入，Query/Update 入参为实体 {@code E}。 */
    public <E, K> BulkWriteResult bulkInsertOnlyDistinct(
            Class<E> entityClass,
            Collection<E> entities,
            Function<E, K> distinctKeyFn,
            Function<E, Query> queryFn,
            Function<E, Update> insertOnlyUpdateFn,
            boolean ordered
    ) {
        return delegate.batchInsertOnlyDistinct(
                entityClass,
                entities,
                distinctKeyFn,
                wrapQuery(queryFn),
                wrapUpdate(insertOnlyUpdateFn),
                ordered
        );
    }

    public <E, K> BulkWriteResult bulkInsertOnlyDistinct(
            Class<E> entityClass,
            Collection<E> entities,
            InsertOnlyDistinctSpec<E, K> spec,
            boolean ordered
    ) {
        return bulkInsertOnlyDistinct(
                entityClass, entities, spec.distinctKeyFn(), spec.queryFn(), spec.updateFn(), ordered);
    }

    /** DTO 去重后转实体，Query/Update 入参为实体 {@code E}。 */
    public <S, E, K> BulkWriteResult bulkInsertOnlyDistinct(
            Class<E> entityClass,
            Collection<S> source,
            Function<S, K> distinctKeyFn,
            Function<S, E> toEntity,
            Function<E, Query> queryFn,
            Function<E, Update> insertOnlyUpdateFn,
            boolean ordered
    ) {
        return delegate.batchInsertOnlyDistinct(
                entityClass,
                source,
                distinctKeyFn,
                toEntity,
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

    private <T> Function<T, Query> wrapQuery(Function<T, Query> queryFn) {
        return item -> DataPermissionCriteria.apply(queryFn.apply(item));
    }

    private <T> Function<T, Update> wrapUpdate(Function<T, Update> updateFn) {
        return item -> wrapUpdateOne(updateFn.apply(item));
    }

    private Update wrapUpdateOne(Update update) {
        return DataPermissionCriteria.applyInsertOrg(update);
    }
}
