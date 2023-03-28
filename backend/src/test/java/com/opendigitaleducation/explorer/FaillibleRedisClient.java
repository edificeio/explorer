package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.ingest.impl.ErrorMessageTransformer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import org.entcore.common.redis.RedisClient;

import java.util.List;

import static java.util.Collections.emptyList;

public class FaillibleRedisClient extends RedisClient {

    private List<ErrorMessageTransformer.IngestJobErrorRule> errorRules = emptyList();

    public FaillibleRedisClient(Redis redis, RedisOptions redisOptions) {
        super(redis, redisOptions);
    }

    public FaillibleRedisClient(Vertx vertx, JsonObject redisConfig) throws Exception {
        super(vertx, redisConfig);
    }

    public void setErrorRules(List<ErrorMessageTransformer.IngestJobErrorRule> errorRules) {
        this.errorRules = errorRules == null ? emptyList() : errorRules;
    }

    @Override
    public Future<List<String>> xAdd(final String stream, final List<JsonObject> jsons) {
        for (JsonObject message : jsons) {
            final boolean matchesOneRule = this.errorRules.stream()
                    .filter(rule -> "redis-xadd".equals(rule.getPointOfFailure()))
                    .anyMatch(errorRule -> messageMatchesErrorRule(message, stream, errorRule));
            if(matchesOneRule) {
                return Future.failedFuture("evicted.by.test.rules");
            }
        }
        return super.xAdd(stream, jsons);
    }

    public boolean messageMatchesErrorRule(final JsonObject message,
                                           final String stream,
                                           final ErrorMessageTransformer.IngestJobErrorRule errorRule) {
        if(errorRule.getValuesToTarget() != null) {
            final JsonObject payload = new JsonObject(message.getString("payload"));
            final boolean bodyMatch = errorRule.getValuesToTarget().entrySet().stream().allMatch(fieldNameAndValue ->
                    payload.getString(fieldNameAndValue.getKey(), "").matches(fieldNameAndValue.getValue())
            );
            if(bodyMatch) {
                log.debug("Evicting message " + message + " based on " + errorRule);
            } else {
                return false;
            }
        }
        if (errorRule.getAction() != null && stream.matches(errorRule.getAction())) {
            return false;
        }
        return true;
    }
}
