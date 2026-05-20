package com.bluebell.mongo.permission;

import com.bluebell.mongo.bulk.MergeUtils;
import com.bluebell.mongo.bulk.TimeForwardCriteria;
import com.bluebell.mongo.bulk.UpsertSpec;
import com.bluebell.mongo.model.*;
import com.bluebell.mongo.support.SnowflakeIdGenerator;
import com.mongodb.bulk.BulkWriteResult;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 带 organizationId 权限的微信四表批量写入。
 * DTO 需带 organizationId；插入时写入，查询/更新自动拼接权限。
 */
public class DataPermissionWxMsgBatchWriter {

    private final DataPermissionMongoBulkHelper bulkHelper;
    private final SnowflakeIdGenerator snowflake;

    public DataPermissionWxMsgBatchWriter(DataPermissionMongoBulkHelper bulkHelper, SnowflakeIdGenerator snowflake) {
        this.bulkHelper = bulkHelper;
        this.snowflake = snowflake;
    }

    public void writeBatchInTransaction(List<WxMsgDTO> batch) {
        batch.forEach(m -> DataPermissionCriteria.assertWritable(m.getOrganizationId()));
        bulkHelper.executeInTransaction(
                () -> writeMain(batch),
                () -> writeLatest(batch),
                () -> writeWxLastTimeBulk(batch),
                () -> writeMsgTypeDict(batch)
        );
    }

    public BulkWriteResult writeMain(List<WxMsgDTO> batch) {
        return bulkHelper.bulkInsertOnly(WxMsgMain.class, batch,
                m -> Query.query(Criteria.where("uniqueId").is(m.getUniqueId())),
                this::buildMainInsertOnlyUpdate, inTransaction());
    }

    public BulkWriteResult writeLatest(List<WxMsgDTO> batch) {
        return bulkHelper.bulkUpsertMerged(WxMsgLatest.class, batch,
                this::sessionKey, MergeUtils.keepNewer(WxMsgDTO::getMsgTime),
                UpsertSpec.of(this::buildLatestQuery, this::buildLatestUpdate), inTransaction());
    }

    public BulkWriteResult writeWxLastTimeBulk(List<WxMsgDTO> batch) {
        record WxTimeItem(String wxId, String organizationId, LocalDateTime maxTime) {}
        Map<String, WxTimeItem> maxByWxOrg = batch.stream()
                .filter(m -> m.getWxId() != null && m.getMsgTime() != null)
                .collect(Collectors.toMap(
                        m -> m.getWxId() + "|" + m.getOrganizationId(),
                        m -> new WxTimeItem(m.getWxId(), m.getOrganizationId(), m.getMsgTime()),
                        (a, b) -> a.maxTime().isAfter(b.maxTime()) ? a : b
                ));
        List<WxTimeItem> items = maxByWxOrg.values().stream().toList();

        return bulkHelper.bulkUpsert(WxLastTime.class, items,
                item -> buildWxTimeQuery(item.wxId(), item.maxTime()),
                item -> buildWxTimeUpdate(item.wxId(), item.organizationId(), item.maxTime()),
                inTransaction());
    }

    public BulkWriteResult writeMsgTypeDict(List<WxMsgDTO> batch) {
        return bulkHelper.bulkInsertOnlyDistinct(WxMsgType.class, batch, WxMsgDTO::getType,
                type -> Query.query(Criteria.where("type").is(type)),
                type -> new Update().setOnInsert("type", type).setOnInsert("createTime", LocalDateTime.now()),
                inTransaction());
    }

    private boolean inTransaction() {
        return bulkHelper.getDelegate().getTransactionTemplate() != null;
    }

    private String sessionKey(WxMsgDTO m) {
        return m.getWxId() + "|" + m.getChatType() + "|" + m.getTalker();
    }

    private Update buildMainInsertOnlyUpdate(WxMsgDTO m) {
        return new Update()
                .setOnInsert("uniqueId", m.getUniqueId())
                .setOnInsert("organizationId", m.getOrganizationId())
                .setOnInsert("wxId", m.getWxId())
                .setOnInsert("chatType", m.getChatType())
                .setOnInsert("talker", m.getTalker())
                .setOnInsert("type", m.getType())
                .setOnInsert("content", m.getContent())
                .setOnInsert("msgTime", m.getMsgTime());
    }

    private Query buildLatestQuery(WxMsgDTO m) {
        return Query.query(Criteria.where("wxId").is(m.getWxId())
                .and("chatType").is(m.getChatType())
                .and("talker").is(m.getTalker())
                .andOperator(TimeForwardCriteria.timeForward("msgTime", m.getMsgTime())));
    }

    private Update buildLatestUpdate(WxMsgDTO m) {
        return new Update()
                .setOnInsert("_id", snowflake.nextId())
                .setOnInsert("organizationId", m.getOrganizationId())
                .setOnInsert("wxId", m.getWxId())
                .setOnInsert("chatType", m.getChatType())
                .setOnInsert("talker", m.getTalker())
                .set("uniqueId", m.getUniqueId())
                .set("type", m.getType())
                .set("content", m.getContent())
                .set("msgTime", m.getMsgTime());
    }

    private Query buildWxTimeQuery(String wxId, LocalDateTime maxTime) {
        return Query.query(Criteria.where("wxId").is(wxId)
                .andOperator(TimeForwardCriteria.timeForwardStrict("lastMsgTime", maxTime)));
    }

    private Update buildWxTimeUpdate(String wxId, String organizationId, LocalDateTime maxTime) {
        return new Update()
                .set("lastMsgTime", maxTime)
                .setOnInsert("wxId", wxId)
                .setOnInsert("organizationId", organizationId);
    }
}
