package com.bluebell.mongo.upsert;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.util.StringUtils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 实体 upsert 元数据（反射解析一次并缓存）。
 */
public final class EntityUpsertDefinition {

    private static final ConcurrentMap<Class<?>, EntityUpsertDefinition> CACHE = new ConcurrentHashMap<>();

    private final Class<?> entityClass;
    private final UpsertStrategy strategy;
    private final String timeField;
    private final String idField;
    private final boolean generateIdOnInsert;
    private final boolean rejectFutureTime;
    private final List<FieldMeta> keyFields;
    private final List<FieldMeta> payloadFields;

    private EntityUpsertDefinition(Class<?> entityClass) {
        this.entityClass = entityClass;
        UpsertEntity ann = entityClass.getAnnotation(UpsertEntity.class);
        if (ann == null) {
            throw new IllegalArgumentException("实体缺少 @UpsertEntity: " + entityClass.getName());
        }
        this.strategy = ann.strategy();
        this.timeField = ann.timeField();
        this.idField = ann.idField();
        this.generateIdOnInsert = ann.generateIdOnInsert();
        this.rejectFutureTime = ann.rejectFutureTime();
        if (rejectFutureTime && !StringUtils.hasText(timeField)) {
            throw new IllegalArgumentException(
                    "rejectFutureTime=true 时必须配置 timeField: " + entityClass.getName());
        }

        if ((strategy == UpsertStrategy.UPSERT_IF_NEWER || strategy == UpsertStrategy.UPSERT_SELECTIVE)
                && StringUtils.hasText(timeField) == false
                && strategy == UpsertStrategy.UPSERT_IF_NEWER) {
            throw new IllegalArgumentException("@UpsertEntity.timeField 必填: " + entityClass.getName());
        }

        List<FieldMeta> keys = new ArrayList<>();
        List<FieldMeta> payloads = new ArrayList<>();

        for (java.lang.reflect.Field f : entityClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            FieldMeta meta = FieldMeta.of(f, ann);
            if (meta.mode() == FieldUpsertMode.KEY) {
                keys.add(meta);
            } else if (meta.mode() != FieldUpsertMode.IGNORE) {
                payloads.add(meta);
            }
        }

        if (keys.isEmpty() && ann.keys().length > 0) {
            for (String keyName : ann.keys()) {
                java.lang.reflect.Field f = findField(entityClass, keyName);
                f.setAccessible(true);
                keys.add(FieldMeta.ofKey(f, keyName));
            }
        }

        if (keys.isEmpty()) {
            throw new IllegalArgumentException("未定义业务主键(@UpsertKey 或 @UpsertEntity.keys): " + entityClass.getName());
        }

        this.keyFields = List.copyOf(keys);
        this.payloadFields = List.copyOf(payloads);
    }

    public static EntityUpsertDefinition of(Class<?> entityClass) {
        return CACHE.computeIfAbsent(entityClass, EntityUpsertDefinition::new);
    }

    public Class<?> entityClass() {
        return entityClass;
    }

    public UpsertStrategy strategy() {
        return strategy;
    }

    public String timeField() {
        return timeField;
    }

    public String idField() {
        return idField;
    }

    public boolean generateIdOnInsert() {
        return generateIdOnInsert;
    }

    public boolean rejectFutureTime() {
        return rejectFutureTime;
    }

    public List<FieldMeta> keyFields() {
        return keyFields;
    }

    public List<FieldMeta> payloadFields() {
        return payloadFields;
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new IllegalArgumentException("字段不存在: " + name + " in " + clazz.getName());
    }

    public record FieldMeta(
            java.lang.reflect.Field javaField,
            String mongoName,
            FieldUpsertMode mode
    ) {
        static FieldMeta of(java.lang.reflect.Field f, UpsertEntity entityAnn) {
            UpsertField fieldAnn = f.getAnnotation(UpsertField.class);
            FieldUpsertMode mode = resolveMode(f, fieldAnn, entityAnn.strategy());
            String mongoName = f.isAnnotationPresent(Id.class) ? "_id" : resolveMongoName(f, fieldAnn);
            return new FieldMeta(f, mongoName, mode);
        }

        static FieldMeta ofKey(java.lang.reflect.Field f, String mongoName) {
            return new FieldMeta(f, mongoName, FieldUpsertMode.KEY);
        }

        private static FieldUpsertMode resolveMode(
                java.lang.reflect.Field f,
                UpsertField fieldAnn,
                UpsertStrategy strategy
        ) {
            if (f.isAnnotationPresent(Id.class)) {
                return FieldUpsertMode.INSERT_ONLY;
            }
            if (f.isAnnotationPresent(UpsertKey.class)) {
                return FieldUpsertMode.KEY;
            }
            if (f.isAnnotationPresent(UpsertOnUpdate.class)) {
                return FieldUpsertMode.ALWAYS;
            }
            if (fieldAnn != null && fieldAnn.mode() != FieldUpsertMode.DEFAULT) {
                return fieldAnn.mode();
            }
            return switch (strategy) {
                case INSERT_ONLY, UPSERT_SELECTIVE -> FieldUpsertMode.INSERT_ONLY;
                case FULL_BY_KEY, UPSERT_IF_NEWER -> FieldUpsertMode.ALWAYS;
            };
        }

        private static String resolveMongoName(java.lang.reflect.Field f, UpsertField fieldAnn) {
            if (fieldAnn != null && StringUtils.hasText(fieldAnn.name())) {
                return fieldAnn.name();
            }
            Field mongoField = f.getAnnotation(Field.class);
            if (mongoField != null && StringUtils.hasText(mongoField.value())) {
                return mongoField.value();
            }
            return f.getName();
        }

        public Object read(Object entity) {
            try {
                return javaField.get(entity);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("读取字段失败: " + javaField.getName(), e);
            }
        }
    }
}
