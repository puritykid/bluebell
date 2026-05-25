package com.bluebell.mongo.upsert;

import com.bluebell.mongo.bulk.BulkWriteMode;
import com.bluebell.mongo.bulk.TimeForwardCriteria;
import com.bluebell.mongo.support.EpochTimeUtils;
import org.springframework.util.StringUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据实体注解 + 反射构建 Query / Update（纯批量工具，不含数据权限）。
 */
public final class ReflectiveUpsertBuilder {

    private ReflectiveUpsertBuilder() {
    }

    public static QueryUpdate build(Object entity, EntityUpsertDefinition def, UpsertIdGenerator idGenerator) {
        return build(entity, def, idGenerator, BulkWriteMode.UPSERT);
    }

    public static QueryUpdate build(
            Object entity,
            EntityUpsertDefinition def,
            UpsertIdGenerator idGenerator,
            BulkWriteMode writeMode
    ) {
        return new QueryUpdate(
                buildQuery(entity, def, writeMode),
                buildUpdate(entity, def, idGenerator, writeMode)
        );
    }

    /**
     * 未来时间等规则下是否跳过本条（不 insert、不 update）。
     */
    public static boolean shouldSkipWrite(Object entity, EntityUpsertDefinition def) {
        if (!def.rejectFutureTime()) {
            return false;
        }
        Object raw = readTimeValue(entity, def);
        Long millis = EpochTimeUtils.normalizeToMillis(raw);
        return EpochTimeUtils.isFuture(millis);
    }

    public static Query buildQuery(Object entity, EntityUpsertDefinition def) {
        return buildQuery(entity, def, BulkWriteMode.UPSERT);
    }

    public static Query buildQuery(Object entity, EntityUpsertDefinition def, BulkWriteMode writeMode) {
        List<Criteria> keyCriteria = new ArrayList<>();
        for (EntityUpsertDefinition.FieldMeta key : def.keyFields()) {
            Object val = key.read(entity);
            keyCriteria.add(Criteria.where(key.mongoName()).is(val));
        }
        Criteria criteria = new Criteria().andOperator(keyCriteria.toArray(new Criteria[0]));

        if (needsTimeForward(def, writeMode)) {
            Object newTime = readTimeValue(entity, def);
            if (newTime != null) {
                criteria = criteria.andOperator(TimeForwardCriteria.timeForward(def.timeField(), newTime));
            }
        }

        return Query.query(criteria);
    }

    public static Update buildUpdate(Object entity, EntityUpsertDefinition def, UpsertIdGenerator idGenerator) {
        return buildUpdate(entity, def, idGenerator, BulkWriteMode.UPSERT);
    }

    public static Update buildUpdate(
            Object entity,
            EntityUpsertDefinition def,
            UpsertIdGenerator idGenerator,
            BulkWriteMode writeMode
    ) {
        return switch (writeMode) {
            case INSERT_ONLY -> buildInsertOnlyUpdate(entity, def, idGenerator);
            case UPDATE_ONLY -> buildUpdateOnlyUpdate(entity, def);
            case UPSERT -> buildUpsertUpdate(entity, def, idGenerator);
        };
    }

    /** 批量新增：仅 setOnInsert */
    private static Update buildInsertOnlyUpdate(
            Object entity,
            EntityUpsertDefinition def,
            UpsertIdGenerator idGenerator
    ) {
        Update update = new Update();
        if (def.generateIdOnInsert() && idGenerator != null && isIdEmpty(entity, def)) {
            update.setOnInsert(def.idField(), idGenerator.nextId(def.entityClass()));
        }
        for (EntityUpsertDefinition.FieldMeta key : def.keyFields()) {
            update.setOnInsert(key.mongoName(), key.read(entity));
        }
        for (EntityUpsertDefinition.FieldMeta field : def.payloadFields()) {
            Object val = field.read(entity);
            if (val == null && field.mode() != FieldUpsertMode.ALWAYS) {
                continue;
            }
            update.setOnInsert(field.mongoName(), val);
        }
        return update;
    }

    /** 批量修改：仅 set，不存在则不插入 */
    private static Update buildUpdateOnlyUpdate(Object entity, EntityUpsertDefinition def) {
        Update update = new Update();
        for (EntityUpsertDefinition.FieldMeta field : def.payloadFields()) {
            if (!isFieldWritableOnUpdate(field)) {
                continue;
            }
            Object val = field.read(entity);
            if (val == null && field.mode() != FieldUpsertMode.ALWAYS) {
                continue;
            }
            update.set(field.mongoName(), val);
        }
        return update;
    }

    /** 批量 upsert：不存在插入、存在按实体策略更新 */
    private static Update buildUpsertUpdate(
            Object entity,
            EntityUpsertDefinition def,
            UpsertIdGenerator idGenerator
    ) {
        Update update = new Update();
        if (def.generateIdOnInsert() && idGenerator != null && isIdEmpty(entity, def)) {
            update.setOnInsert(def.idField(), idGenerator.nextId(def.entityClass()));
        }
        for (EntityUpsertDefinition.FieldMeta key : def.keyFields()) {
            update.setOnInsert(key.mongoName(), key.read(entity));
        }
        for (EntityUpsertDefinition.FieldMeta field : def.payloadFields()) {
            Object val = field.read(entity);
            if (val == null && field.mode() != FieldUpsertMode.ALWAYS) {
                continue;
            }
            switch (field.mode()) {
                case INSERT_ONLY -> update.setOnInsert(field.mongoName(), val);
                case ALWAYS -> applyAlwaysField(update, def, field.mongoName(), val);
                default -> { }
            }
        }
        return update;
    }

    private static boolean isFieldWritableOnUpdate(EntityUpsertDefinition.FieldMeta field) {
        return field.mode() == FieldUpsertMode.ALWAYS;
    }

    private static Object readTimeValue(Object entity, EntityUpsertDefinition def) {
        for (EntityUpsertDefinition.FieldMeta field : def.payloadFields()) {
            if (def.timeField().equals(field.mongoName())) {
                return field.read(entity);
            }
        }
        for (EntityUpsertDefinition.FieldMeta key : def.keyFields()) {
            if (def.timeField().equals(key.mongoName())) {
                return key.read(entity);
            }
        }
        return readFieldByMongoName(entity, def, def.timeField());
    }

    private static Object readFieldByMongoName(Object entity, EntityUpsertDefinition def, String mongoName) {
        for (EntityUpsertDefinition.FieldMeta f : def.payloadFields()) {
            if (mongoName.equals(f.mongoName())) {
                return f.read(entity);
            }
        }
        for (EntityUpsertDefinition.FieldMeta f : def.keyFields()) {
            if (mongoName.equals(f.mongoName())) {
                return f.read(entity);
            }
        }
        throw new IllegalArgumentException("timeField 未在实体中找到: " + mongoName);
    }

    private static boolean isIdEmpty(Object entity, EntityUpsertDefinition def) {
        Object idVal = readIdValue(entity, def);
        if (idVal == null) {
            return true;
        }
        if (idVal instanceof String s) {
            return s.isBlank();
        }
        if (idVal instanceof Number n) {
            return n.longValue() == 0L;
        }
        return false;
    }

    private static Object readIdValue(Object entity, EntityUpsertDefinition def) {
        for (EntityUpsertDefinition.FieldMeta field : def.payloadFields()) {
            if (def.idField().equals(field.mongoName())) {
                return field.read(entity);
            }
        }
        try {
            java.lang.reflect.Field f = entity.getClass().getDeclaredField(
                    "_id".equals(def.idField()) ? "id" : def.idField());
            f.setAccessible(true);
            return f.get(entity);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return readFieldByMongoName(entity, def, def.idField());
        }
    }

    private static boolean needsTimeForward(EntityUpsertDefinition def, BulkWriteMode writeMode) {
        if (!StringUtils.hasText(def.timeField())) {
            return false;
        }
        if (writeMode == BulkWriteMode.INSERT_ONLY) {
            return false;
        }
        if (writeMode == BulkWriteMode.UPDATE_ONLY) {
            return true;
        }
        if (def.strategy() == UpsertStrategy.UPSERT_IF_NEWER) {
            return true;
        }
        return def.strategy() == UpsertStrategy.UPSERT_SELECTIVE;
    }

    /** 存在时 set；插入时 set + setOnInsert，保证新文档也有值 */
    private static void applyAlwaysField(Update update, EntityUpsertDefinition def, String name, Object val) {
        update.set(name, val);
        if (def.strategy() == UpsertStrategy.INSERT_ONLY
                || def.strategy() == UpsertStrategy.UPSERT_SELECTIVE) {
            update.setOnInsert(name, val);
        }
    }

    public record QueryUpdate(Query query, Update update) {
    }
}
