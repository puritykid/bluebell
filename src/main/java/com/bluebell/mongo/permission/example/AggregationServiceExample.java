package com.bluebell.mongo.permission.example;

import com.bluebell.mongo.permission.DataPermission;
import com.bluebell.mongo.permission.DataPermissionAggregation;
import com.bluebell.mongo.permission.DataPermissionCriteria;
import com.bluebell.mongo.permission.DataPermissionMongoTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

/**
 * Aggregation 三种写法示例（Service 上加 @DataPermission）。
 */
@Service
@RequiredArgsConstructor
public class AggregationServiceExample {

    /** 方式1：注入 DataPermissionMongoTemplate，aggregate 自动 prepend $match */
    private final DataPermissionMongoTemplate mongoTemplate;

    @DataPermission
    public AggregationResults<WxStatVO> statByWx() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.group("wxId").count().as("cnt"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "cnt")
        );
        // 实际执行等价于：$match{organizationId:in(...)} → $group → $sort
        return mongoTemplate.aggregate(agg, "wx_msg_main", WxStatVO.class);
    }

    /** 方式2：普通 MongoTemplate，手动 prepend */
    @DataPermission
    public AggregationResults<WxStatVO> statManual(org.springframework.data.mongodb.core.MongoTemplate template) {
        Aggregation raw = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(1)),
                Aggregation.group("wxId").count().as("cnt")
        );
        Aggregation withPerm = DataPermissionAggregation.prependOrgMatch(raw);
        return template.aggregate(withPerm, "wx_msg_main", WxStatVO.class);
    }

    /** 方式3：构建时把权限写进 match */
    @DataPermission
    public AggregationResults<WxStatVO> statCombined() {
        Criteria criteria = DataPermissionAggregation.andOrg(Criteria.where("status").is(1));
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("wxId").count().as("cnt")
        );
        return mongoTemplate.aggregate(agg, "wx_msg_main", WxStatVO.class);
        // 注意：若用 DataPermissionMongoTemplate 会再加一层 $match，应二选一
    }

    /** 方式4：$lookup 子管道也要权限时 */
    @DataPermission
    public AggregationResults<WxStatVO> statWithLookup() {
        Aggregation agg = DataPermissionAggregation.of(
                Aggregation.match(Criteria.where("deleted").is(false)),
                Aggregation.lookup("wx_msg_latest", "wxId", "wxId", "latest"),
                Aggregation.unwind("latest"),
                Aggregation.group("wxId").count().as("cnt")
        );
        return mongoTemplate.aggregate(agg, "wx_msg_main", WxStatVO.class);
    }

    public record WxStatVO(String wxId, long cnt) {}
}
