package com.xuexi.zijie.aggreagte.cep;

/**
 * Copyright (c) 2020-2030 尚硅谷 All Rights Reserved
 * <p>
 * Project:  FlinkTutorial
 * <p>
 * Created by  wushengran
 */

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class 完成_LoginFailDetectExample {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 1. 获取登录事件流，并提取时间戳、生成水位线
        KeyedStream<完成_LoginEvent, String> stream = env
                .fromElements(
                        new 完成_LoginEvent("user_1", "192.168.0.1", "fail", 2000L),
                        new 完成_LoginEvent("user_1", "192.168.0.2", "fail", 3000L),
                        new 完成_LoginEvent("user_2", "192.168.1.29", "fail", 4000L),
                        new 完成_LoginEvent("user_1", "171.56.23.10", "fail", 5000L),
                        new 完成_LoginEvent("user_2", "192.168.1.29", "fail", 7000L),
                        new 完成_LoginEvent("user_2", "192.168.1.29", "fail", 8000L),
                        new 完成_LoginEvent("user_2", "192.168.1.29", "success", 6000L)
                )
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<完成_LoginEvent>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner(
                                        new SerializableTimestampAssigner<完成_LoginEvent>() {
                                            @Override
                                            public long extractTimestamp(完成_LoginEvent loginEvent, long l) {
                                                return loginEvent.timestamp;
                                            }
                                        }
                                )
                )
                .keyBy(r -> r.userId);

        // 2. 定义Pattern，连续的三个登录失败事件
        Pattern<完成_LoginEvent, 完成_LoginEvent> pattern = Pattern.<完成_LoginEvent>begin("first")    // 以第一个登录失败事件开始
                .where(new SimpleCondition<完成_LoginEvent>() {
                    @Override
                    public boolean filter(完成_LoginEvent loginEvent) throws Exception {
                        return loginEvent.eventType.equals("fail");
                    }
                })
                .next("second")    // 接着是第二个登录失败事件
                .where(new SimpleCondition<完成_LoginEvent>() {
                    @Override
                    public boolean filter(完成_LoginEvent loginEvent) throws Exception {
                        return loginEvent.eventType.equals("fail");
                    }
                })
                .next("third")     // 接着是第三个登录失败事件
                .where(new SimpleCondition<完成_LoginEvent>() {
                    @Override
                    public boolean filter(完成_LoginEvent loginEvent) throws Exception {
                        return loginEvent.eventType.equals("fail");
                    }
                });

        // 3. 将Pattern应用到流上，检测匹配的复杂事件，得到一个PatternStream
        PatternStream<完成_LoginEvent> patternStream = CEP.pattern(stream, pattern);

        // 4. 将匹配到的复杂事件选择出来，然后包装成字符串报警信息输出
        patternStream
                .select(new PatternSelectFunction<完成_LoginEvent, String>() {
                    @Override
                    public String select(Map<String, List<完成_LoginEvent>> map) throws Exception {
                        完成_LoginEvent first = map.get("first").get(0);
                        完成_LoginEvent second = map.get("second").get(0);
                        完成_LoginEvent third = map.get("third").get(0);
                        return first.userId + " 连续三次登录失败！登录时间：" + first.timestamp + ", " + second.timestamp + ", " + third.timestamp;
                    }
                })
                .print("warning");

        env.execute();
    }
}
