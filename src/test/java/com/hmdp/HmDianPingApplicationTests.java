package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Autowired
    public StringRedisTemplate stringRedisTemplate;

    @Test
    public void test(){
    shopService.saveShop2Redis(1L, 1000L);
    }
    @Test
    public void test2(){stringRedisTemplate.opsForValue().set("liu","ziyu");}
}
