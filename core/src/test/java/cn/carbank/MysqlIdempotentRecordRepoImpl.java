package cn.carbank;

import cn.carbank.constant.DateField;
import cn.carbank.repository.IdempotentRecordRepo;
import cn.carbank.utils.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * mysql存储
 *
 * @author 周承钲(chengzheng.zhou @ ucarinc.com)
 * @since 2020年12月14日
 */
public class MysqlIdempotentRecordRepoImpl implements IdempotentRecordRepo {
    /** 日志记录器 */
    private final Logger logger = LoggerFactory.getLogger(MysqlIdempotentRecordRepoImpl.class);

    Map<String, IdempotentRecord> cache = new HashMap<>();

    @Override
    public boolean add(String key, String value, int expireTime, TimeUnit timeUnit) {
        logger.error("mysql add {}", key);
        IdempotentRecord idempotentRecord = new IdempotentRecord();
        idempotentRecord.setKey(key);
        idempotentRecord.setValue(value);
        Date now = new Date();
        idempotentRecord.setAddTime(now);
        if (expireTime > 0) {
            Date offset = DateUtil.offset(now, DateField.of(timeUnit), expireTime);
            idempotentRecord.setExpireTime(offset);
        }
        cache.put(key, idempotentRecord);
        return true;
    }

    @Override
    public boolean exist(String key) {
        logger.error("mysql exist {}", key);
        return cache.containsKey(key);
    }

    @Override
    public IdempotentRecord get(String key) {
        logger.error("mysql get {}", key);
        return cache.get(key);
    }

    @Override
    public boolean delete(String key) {
        logger.error("mysql delete {}", key);
        cache.remove(key);
        return true;
    }

}
