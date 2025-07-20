package com.xuexi.zijie.bean;

import org.apache.flink.api.common.functions.MapFunction;

/**
 *  自定义MapFunction： 字符串映射到用户登陆类
 */
public class WaterSensorMapFunction implements MapFunction<String,WaterSensor> {
    @Override
    public WaterSensor map(String value) throws Exception {
        String[] datas = value.split(",");
        return new WaterSensor(datas[0], Long.valueOf(datas[1]), Integer.valueOf(datas[2]));
    }
}
