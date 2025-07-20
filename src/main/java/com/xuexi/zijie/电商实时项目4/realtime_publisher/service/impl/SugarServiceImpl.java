package com.xuexi.zijie.电商实时项目4.realtime_publisher.service.impl;


import com.xuexi.zijie.电商实时项目4.realtime_publisher.mapper.SugarMapper;
import com.xuexi.zijie.电商实时项目4.realtime_publisher.service.SugarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SugarServiceImpl implements SugarService {

    @Autowired
    private SugarMapper sugarMapper;

    @Override
    public BigDecimal getGmv(String date) {
        // 核心业务逻辑
        return sugarMapper.selectGmv(date);
    }

    @Override
    public Map<String, BigDecimal> getGmvByTrademark(String date, int limit) {

        //查询数据
        List<Map<String, Object>> mapList = sugarMapper.selectGmvByTrademark(date, limit);

        //创建Map存放结果数据
        HashMap<String, BigDecimal> resultMap = new HashMap<>();

        //遍历mapList,将数据封装进resultMap
        for (Map<String, Object> map : mapList) {
            resultMap.put((String) map.get("trademark_name"), (BigDecimal) map.get("order_amount"));
        }

        //返回结果
        return resultMap;

    }
    @Override
    public Map<String, Long> getOrderCountByProvince(String date) {

        //查询数据
        List<Map<String, Object>> mapList = sugarMapper.selectOrderCountByProvince(date);

        //创建Map存放结果数据
        HashMap<String, Long> resultMap = new HashMap<>();

        //遍历mapList,将数据封装进resultMap
        for (Map<String, Object> map : mapList) {
            resultMap.put((String) map.get("province_name"), (Long) map.get("counts"));
        }

        //返回结果
        return resultMap;

    }
}
