package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;


    @Override
    public Result seckillVoucher(Long voucherId) {
//        1、查询秒杀券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        2、判断秒杀券是否合法 判断是否在开始时间之后，结束之前
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始！");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀活动已结束！");
        }
//        4、判断库存是否充足
        if(voucher.getStock()<1) return Result.fail("秒杀券库存不足");
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){//toString和intern缺一不可
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, userId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId) {
        //        4.1 判断是否是第一次下单
        int count = this.count(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId));
        if(count>0) return Result.fail("该用户已购买过");
//        5、库存-1
        LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0)
                .setSql("stock = stock -1");
        if(!seckillVoucherService.update(updateWrapper)) throw new RuntimeException("秒杀券扣减失败");
//        6、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long id = redisIdWorker.nextID(SECKILL_ORDER);
        voucherOrder.setId(id);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        boolean save = save(voucherOrder);
        if(!save) throw new RuntimeException("创建订单失败！");
        return Result.ok(id);
    }
}
