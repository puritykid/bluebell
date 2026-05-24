package com.bluebell.mongo.upsert;

import com.bluebell.mongo.model.*;
import com.bluebell.mongo.support.EpochTimeUtils;

import java.util.List;
import java.util.stream.Collectors;

/** DTO → 注解实体（字段同名映射） */
public final class WxMsgEntityMapper {

    private WxMsgEntityMapper() {
    }

    public static WxMsgMain toMain(com.bluebell.mongo.model.WxMsgDTO dto) {
        WxMsgMain m = new WxMsgMain();
        copy(dto, m);
        return m;
    }

    public static WxMsgLatest toLatest(com.bluebell.mongo.model.WxMsgDTO dto) {
        WxMsgLatest m = new WxMsgLatest();
        copy(dto, m);
        return m;
    }

    public static List<WxLastTime> toLastTimeList(List<com.bluebell.mongo.model.WxMsgDTO> batch) {
        return batch.stream()
                .filter(d -> d.getWxId() != null && d.getMsgTime() != null)
                .collect(Collectors.groupingBy(
                        com.bluebell.mongo.model.WxMsgDTO::getWxId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(java.util.Comparator.comparing(
                                        com.bluebell.mongo.model.WxMsgDTO::getMsgTime)),
                                opt -> opt.map(d -> {
                                    WxLastTime t = new WxLastTime();
                                    t.setWxId(d.getWxId());
                                    t.setOrganizationId(d.getOrganizationId());
                                    t.setLastMsgTime(d.getMsgTime());
                                    return t;
                                }).orElse(null)
                        )
                ))
                .values().stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public static List<WxMsgType> toTypeList(List<com.bluebell.mongo.model.WxMsgDTO> batch) {
        return batch.stream()
                .map(com.bluebell.mongo.model.WxMsgDTO::getType)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .map(type -> {
                    WxMsgType e = new WxMsgType();
                    e.setType(type);
                    e.setCreateTime(EpochTimeUtils.currentTimeMillis());
                    return e;
                })
                .toList();
    }

    public static List<WxMsgMain> toMainList(List<com.bluebell.mongo.model.WxMsgDTO> batch) {
        return batch.stream().map(WxMsgEntityMapper::toMain).toList();
    }

    public static List<WxMsgLatest> toLatestList(List<com.bluebell.mongo.model.WxMsgDTO> batch) {
        return batch.stream().map(WxMsgEntityMapper::toLatest).toList();
    }

    private static void copy(com.bluebell.mongo.model.WxMsgDTO from, Object to) {
        var dtoFields = com.bluebell.mongo.model.WxMsgDTO.class.getDeclaredFields();
        for (var df : dtoFields) {
            df.setAccessible(true);
            try {
                Object val = df.get(from);
                if (val == null) {
                    continue;
                }
                if (isTimeField(df.getName())) {
                    val = EpochTimeUtils.normalizeToMillis(val);
                }
                try {
                    var tf = to.getClass().getDeclaredField(df.getName());
                    tf.setAccessible(true);
                    tf.set(to, val);
                } catch (NoSuchFieldException ignored) {
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static boolean isTimeField(String name) {
        return "msgTime".equals(name) || "lastMsgTime".equals(name) || "createTime".equals(name);
    }
}
