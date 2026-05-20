package com.bluebell.mongo.bulk;

import com.bluebell.mongo.model.WxLastTime;
import com.bluebell.mongo.model.WxMsgLatest;
import com.bluebell.mongo.model.WxMsgMain;
import com.bluebell.mongo.model.WxMsgType;
import com.bluebell.mongo.model.WxMsgDTO;
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
 * 微信消息四表批量写入（基于 {@link MongoBulkHelper}）。
 * <p>
 * 规则：
 * <ul>
 *   <li>主表：uniqueId 没有才插</li>
 *   <li>最新消息：wxId+chatType+talker，没有才生成雪花 _id，存在则更新，且 msgTime 只向前</li>
 *   <li>微信最新时间：wxId，lastMsgTime 只向前</li>
 *   <li>消息类型：全局字典 type，没有才插</li>
 * </ul>
 */
public class WxMsgBatchWriter {

    private final MongoBulkHelper bulkHelper;
    private final SnowflakeIdGenerator snowflake;

    public WxMsgBatchWriter(MongoBulkHelper bulkHelper, SnowflakeIdGenerator snowflake) {
        this.bulkHelper = bulkHelper;
        this.snowflake = snowflake;
    }

    /**
     * 一批数据四表同事务写入（建议每批 100~500 条）。
     */
    public void writeBatchInTransaction(List<WxMsgDTO> batch) {
        bulkHelper.executeInTransaction(
                () -> writeMain(batch),
                () -> writeLatest(batch),
                () -> writeWxLastTimeBulk(batch),
                () -> writeMsgTypeDict(batch)
        );
    }

    /**
     * 无事务四表写入（更快；不要求四表原子一致性时使用）。
     */
    public void writeBatchWithoutTransaction(List<WxMsgDTO> batch) {
        writeMain(batch);
        writeLatest(batch);
        writeWxLastTimeBulk(batch);
        writeMsgTypeDict(batch);
    }

    public BulkWriteResult writeMain(List<WxMsgDTO> batch) {
        return bulkHelper.bulkInsertOnly(
                WxMsgMain.class,
                batch,
                m -> Query.query(Criteria.where("uniqueId").is(m.getUniqueId())),
                this::buildMainInsertOnlyUpdate,
                inTransaction()
        );
    }

    public BulkWriteResult writeLatest(List<WxMsgDTO> batch) {
        return bulkHelper.bulkUpsertMerged(
                WxMsgLatest.class,
                batch,
                this::sessionKey,
                MergeUtils.keepNewer(WxMsgDTO::getMsgTime),
                UpsertSpec.of(this::buildLatestQuery, this::buildLatestUpdate),
                inTransaction()
        );
    }

    /**
     * wxId 维度合并为一次 bulk（避免每个 wxId 单独 execute）。
     */
    public BulkWriteResult writeWxLastTimeBulk(List<WxMsgDTO> batch) {
        Map<String, LocalDateTime> maxByWx = batch.stream()
                .collect(Collectors.groupingBy(
                        WxMsgDTO::getWxId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(WxMsgDTO::getMsgTime)),
                                opt -> opt.map(WxMsgDTO::getMsgTime).orElse(null)
                        )
                ));

        record WxTimeItem(String wxId, LocalDateTime maxTime) {
        }
        List<WxTimeItem> items = maxByWx.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .map(e -> new WxTimeItem(e.getKey(), e.getValue()))
                .toList();

        return bulkHelper.bulkUpsert(
                WxLastTime.class,
                items,
                item -> buildWxTimeQuery(item.wxId(), item.maxTime()),
                item -> buildWxTimeUpdate(item.wxId(), item.maxTime()),
                inTransaction()
        );
    }

    public BulkWriteResult writeMsgTypeDict(List<WxMsgDTO> batch) {
        return bulkHelper.bulkInsertOnlyDistinct(
                WxMsgType.class,
                batch,
                WxMsgDTO::getType,
                type -> Query.query(Criteria.where("type").is(type)),
                type -> new Update()
                        .setOnInsert("type", type)
                        .setOnInsert("createTime", LocalDateTime.now()),
                inTransaction()
        );
    }

    private boolean inTransaction() {
        return bulkHelper.getTransactionTemplate() != null;
    }

    private String sessionKey(WxMsgDTO m) {
        return m.getWxId() + "|" + m.getChatType() + "|" + m.getTalker();
    }

    private Update buildMainInsertOnlyUpdate(WxMsgDTO m) {
        return new Update()
                .setOnInsert("uniqueId", m.getUniqueId())
                .setOnInsert("wxId", m.getWxId())
                .setOnInsert("chatType", m.getChatType())
                .setOnInsert("talker", m.getTalker())
                .setOnInsert("type", m.getType())
                .setOnInsert("content", m.getContent())
                .setOnInsert("msgTime", m.getMsgTime());
    }

    private Query buildLatestQuery(WxMsgDTO m) {
        return Query.query(
                Criteria.where("wxId").is(m.getWxId())
                        .and("chatType").is(m.getChatType())
                        .and("talker").is(m.getTalker())
                        .andOperator(TimeForwardCriteria.timeForward("msgTime", m.getMsgTime()))
        );
    }

    private Update buildLatestUpdate(WxMsgDTO m) {
        return new Update()
                .setOnInsert("_id", snowflake.nextId())
                .setOnInsert("wxId", m.getWxId())
                .setOnInsert("chatType", m.getChatType())
                .setOnInsert("talker", m.getTalker())
                .set("uniqueId", m.getUniqueId())
                .set("type", m.getType())
                .set("content", m.getContent())
                .set("msgTime", m.getMsgTime());
    }

    private Query buildWxTimeQuery(String wxId, LocalDateTime maxTime) {
        return Query.query(
                Criteria.where("wxId").is(wxId)
                        .andOperator(TimeForwardCriteria.timeForwardStrict("lastMsgTime", maxTime))
        );
    }

    private Update buildWxTimeUpdate(String wxId, LocalDateTime maxTime) {
        return new Update()
                .set("lastMsgTime", maxTime)
                .setOnInsert("wxId", wxId);
    }
}
