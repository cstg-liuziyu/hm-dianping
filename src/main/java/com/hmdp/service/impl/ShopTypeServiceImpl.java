package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        List<String> shopTypes = stringRedisTemplate.opsForList().range(CACHE_SHOP_LIST_KEY, 0, -1);
        if(!shopTypes.isEmpty()){
            List<ShopType> list = new ArrayList<>();
            for(String shopType:shopTypes){
                ShopType bean = JSONUtil.toBean(shopType, ShopType.class);
                list.add(bean);
            }
            return Result.ok(list);
        }
        List<ShopType> list = query().orderByAsc("sort").list();
        if(list==null){
            return Result.fail("店铺类型不存在！");
        }
        for(ShopType shopType:list){
            String jsonStr = JSONUtil.toJsonStr(shopType);
            shopTypes.add(jsonStr);
        }
        stringRedisTemplate.opsForList().leftPushAll(CACHE_SHOP_LIST_KEY, shopTypes);
        return Result.ok(list);
    }
}
