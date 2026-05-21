package com.bluebell.mongo.upsert;

import com.bluebell.mongo.bulk.TimeForwardCriteria;
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
        Query query = buildQuery(entity, def);
        Update update = buildUpdate(entity, def, idGenerator);
        return new QueryUpdate(query, update);
    }

    public static Query buildQuery(Object entity, EntityUpsertDefinition def) {
        List<Criteria> keyCriteria = new ArrayList<>();
        for (EntityUpsertDefinition.FieldMeta key : def.keyFields()) {
            Object val = key.read(entity);
            keyCriteria.add(Criteria.where(key.mongoName()).is(val));
        }
        Criteria criteria = new Criteria().andOperator(keyCriteria.toArray(new Criteria[0]));

        if (def.strategy() == UpsertStrategy.UPSERT_IF_NEWER) {
            Object newTime = readTimeValue(entity, def);
            criteria = criteria.andOperator(TimeForwardCriteria.timeForward(def.timeField(), newTime));
        }

        return Query.query(criteria);
    }

    public static Update buildUpdate(Object entity, EntityUpsertDefinition def, UpsertIdGenerator idGenerator) {
        Update update = new Update();

        if (def.generateIdOnInsert() && idGenerator != null && isIdEmpty(entity, def)) {
            update.setOnInsert(def.idField(), idGenerator.nextId(def.entityClass()));
        }

        for (EntityUpsertDefinition.FieldMeta key : def.keyFields()) {
            Object val = key.read(entity);
            update.setOnInsert(key.mongoName(), val);
        }

        for (EntityUpsertDefinition.FieldMeta field : def.payloadFields()) {
            Object val = field.read(entity);
            if (val == null && field.mode() != FieldUpsertMode.ALWAYS) {
                continue;
            }
            switch (field.mode()) {
                case INSERT_ONLY -> update.setOnInsert(field.mongoName(), val);
                case ALWAYS -> update.set(field.mongoName(), val);
                default -> { }
            }
        }

        return update;
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

    public record QueryUpdate(Query query, Update update) {
    }
}
