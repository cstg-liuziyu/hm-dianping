package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopById(Long id) {
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if(Objects.nonNull(shopJson)){
            return Result.fail("店铺不存在");
        }
        //此时shopJson为NULL
        Shop shop = getById(id);
        if(shop==null){
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    @Override
    public Result update(Shop shop) {
        if(shop.getId()==null){
            Result.fail("店铺id不能为空！");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryWithPassThrough(Long id) {
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if(Objects.nonNull(shopJson)){
            return Result.fail("店铺不存在");
        }
        //此时shopJson为NULL
        Shop shop = getById(id);
        if(shop==null){
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    @Override
    public Result queryWithMutex(Long id) {
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if(Objects.nonNull(shopJson)){
            return Result.fail("店铺不存在");
        }
        try {
            boolean flag = tryLock(LOCK_SHOP_KEY+id);
            if(!flag){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            Shop shop = getById(id);
            if(Objects.isNull(shop)){
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shop);
        } catch (InterruptedException e) {
            throw new RuntimeException("发生异常");
        } finally {
            unLock(LOCK_SHOP_KEY+id);
        }
    }

    private boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryWithLogicalExpire(Long id) {
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(StrUtil.isBlank(shopJson)){
            return Result.fail("店铺不存在");
        }
        RedisData shopdata = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) shopdata.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = shopdata.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return Result.ok(shop);
        }
        boolean b = tryLock(LOCK_SHOP_KEY + id);
        if(b){
            CACHE_REBUILD_EXECUTOR.submit(()-> {
                try {
                    saveShop2Redis(id, CACHE_SHOP_LOGICAL_TTL);
                } finally {
                    unLock(LOCK_SHOP_KEY + id);
                }
            });
        }
        //双检
        String shopJson2 = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        RedisData shopdata2 = JSONUtil.toBean(shopJson2, RedisData.class);
        JSONObject data1 = (JSONObject) shopdata2.getData();
        Shop shop1 = JSONUtil.toBean(data1, Shop.class);
        LocalDateTime expireTime1 = shopdata2.getExpireTime();
        if(expireTime1.isAfter(LocalDateTime.now())){
            return Result.ok(shop1);
        }

        return Result.ok(shop1);
    }


    public void saveShop2Redis(Long id, Long expireTime) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));
    }

}
