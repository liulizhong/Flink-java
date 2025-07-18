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
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class 完成_LoginFailDetectProExample {
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
        Pattern<完成_LoginEvent, 完成_LoginEvent> pattern = Pattern.<完成_LoginEvent>begin("fail")    // 第一个登录失败事件
                .where(new SimpleCondition<完成_LoginEvent>() {
                    @Override
                    public boolean filter(完成_LoginEvent loginEvent) throws Exception {
                        return loginEvent.eventType.equals("fail");
                    }
                }).times(3).consecutive();    // 指定是严格紧邻的三次登录失败

        // 3. 将Pattern应用到流上，检测匹配的复杂事件，得到一个PatternStream
        PatternStream<完成_LoginEvent> patternStream = CEP.pattern(stream, pattern);

        // 4. 将匹配到的复杂事件选择出来，然后包装成字符串报警信息输出
        SingleOutputStreamOperator<String> warningStream = patternStream
                .process(new PatternProcessFunction<完成_LoginEvent, String>() {
                    @Override
                    public void processMatch(Map<String, List<完成_LoginEvent>> match, Context ctx, Collector<String> out) throws Exception {
                        // 提取三次登录失败事件
                        完成_LoginEvent firstFailEvent = match.get("fail").get(0);
                        完成_LoginEvent secondFailEvent = match.get("fail").get(1);
                        完成_LoginEvent thirdFailEvent = match.get("fail").get(2);

                        out.collect(firstFailEvent.userId + " 连续三次登录失败！登录时间：" +
                                firstFailEvent.timestamp + ", " +
                                secondFailEvent.timestamp + ", " +
                                thirdFailEvent.timestamp);
                    }
                });

        // 打印输出
        warningStream.print("warning");

        env.execute();
    }
}

