package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.naming.event.ObjectChangeListener;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //方法1：将任意对象封装为Json并存入Redis的String类型的Key中，并设置过期时间TTL
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    //方法2： 将任意对象封装为Json并存入Redis的String类型的Key中，并设置逻辑过期时间，用于处理缓存击穿
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //方法3：根据指定key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R, ID> R queryWithPassTrough(String keyPrefix, ID id, Class<R> type, Long time, TimeUnit timeUnit, Function<ID, R> dbFallback){
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        if(json!=null){
            return null;
        }
        //此时json为null
        R r = dbFallback.apply(id);
        if(r==null){
            stringRedisTemplate.opsForValue().set(keyPrefix+id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(keyPrefix+id, r, time, timeUnit);
        return r;
    }

    //方法4：根据指定key查询缓存，并反序列化为指定类型，利用逻辑过期解决缓存击穿问题
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit
    ){
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Object data = redisData.getData();
        R r = JSONUtil.toBean((JSONObject) data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        String localKey = LOCK_SHOP_KEY+id;
        boolean b = tryLock(localKey);
        if(b){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try{
                    R r1 = dbFallback.apply(id);
                    setWithLogicalExpire(key,r1,time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(localKey);
                }
            });
        }
        return r;
    }

    public boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }
    public void unLock(String key){stringRedisTemplate.delete(key);}
}
