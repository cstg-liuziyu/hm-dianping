package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
//import org.junit.Test;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

//@SpringBootTest
//public class RedisWorkerIDTests {
//
//    @Resource
//    private RedisIdWorker redisIdWorker;
//
//    ExecutorService executorService = Executors.newFixedThreadPool(500);
//
//    @Test
//    public void Test() throws InterruptedException {
//        CountDownLatch latch = new CountDownLatch(300);
//
//        Runnable task = ()->{
//            for(int i=0;i<100;i++){
//                long id = redisIdWorker.nextID("order");
//                System.out.println(id);
//            }
//            latch.countDown();
//        };
//        long beginTime = System.currentTimeMillis();
//        for(int i=0;i<300;i++){
//            executorService.submit(task);
//        }
//        latch.await();
//        long endTime = System.currentTimeMillis();
//        System.out.println(endTime-beginTime);
//    }
//
//}

@SpringBootTest
public class RedisWorkerIDTests {

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    /**
     * 测试分布式ID生成器的性能，以及可用性
     */
    @Test
    public void testNextId() throws InterruptedException {
        // 使用CountDownLatch让线程同步等待
        CountDownLatch latch = new CountDownLatch(300);
        // 创建线程任务
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextID("order");
                System.out.println("id = " + id);
            }
            // 等待次数-1
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        // 创建300个线程，每个线程创建100个id，总计生成3w个id
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        // 线程阻塞，直到计数器归0时才全部唤醒所有线程
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("生成3w个id共耗时" + (end - begin) + "ms");
    }
}

