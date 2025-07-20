package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.LoginEvent;
import com.xuexi.zijie.bean.SubEvent;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class _18_附一章_FlinkCEP {

    /**
     * 1、FlinkCEP：案例：检测用户行为，如果连续三次登录失败
     */
    @Test
    public void test1() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 获取登录事件流，并提取时间戳、生成水位线
        KeyedStream<LoginEvent, String> stream = env
                .fromElements(
                        new LoginEvent("user_1", "192.168.0.1", "fail", 2000L),
                        new LoginEvent("user_1", "192.168.0.2", "fail", 3000L),
                        new LoginEvent("user_2", "192.168.1.29", "fail", 4000L),
                        new LoginEvent("user_1", "171.56.23.10", "fail", 5000L),
                        new LoginEvent("user_2", "192.168.1.29", "success", 6000L),
                        new LoginEvent("user_2", "192.168.1.29", "fail", 7000L),
                        new LoginEvent("user_2", "192.168.1.29", "fail", 8000L)
                )
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<LoginEvent>forMonotonousTimestamps()
                                .withTimestampAssigner(
                                        new SerializableTimestampAssigner<LoginEvent>() {
                                            @Override
                                            public long extractTimestamp(LoginEvent loginEvent, long l) {
                                                return loginEvent.timestamp;
                                            }
                                        }
                                )
                )
                .keyBy(r -> r.userId);

        // 1. 定义Pattern，连续的三个登录失败事件
        Pattern<LoginEvent, LoginEvent> pattern = Pattern
                // 以第一个登录失败事件开始
                .<LoginEvent>begin("first")
                .where(new SimpleCondition<LoginEvent>() {
                    @Override
                    public boolean filter(LoginEvent loginEvent) throws Exception {
                        return loginEvent.eventType.equals("fail");
                    }
                })
                // 接着是第二个登录失败事件
                .next("second")
                .where(new SimpleCondition<LoginEvent>() {
                    @Override
                    public boolean filter(LoginEvent loginEvent) throws Exception {
                        return loginEvent.eventType.equals("fail");
                    }
                })
                // 接着是第三个登录失败事件
                .next("third")
                .where(new SimpleCondition<LoginEvent>() {
                    @Override
                    public boolean filter(LoginEvent loginEvent) throws Exception {
                        return loginEvent.eventType.equals("fail");
                    }
                });

        // 1.2 测试规则量词
        pattern.oneOrMore();                        // 个体模式出现一次或多次
        pattern.times(3);                           // 匹配事件出现3次
        pattern.times(2,4);                         // 匹配事件出现2, 3 或者4次
        pattern.times(2,4).greedy();                // 使当前循环模式变得“贪心”：如果出现了连续4个a，那么会直接把aaaa检测出来进行处理，其他任意2个a是不算匹配事件的。
        pattern.times(4).optional();                // 使当前模式成为可选的，也就是说可以满足这个匹配条件，也可以不满足。【匹配事件出现4次，或者不出现】
        pattern.times(2, 4).optional();             // 匹配事件出现2, 3, 4次，或者不出现
        pattern.times(2, 4).optional().greedy();    // 匹配事件出现2, 3, 4次，或者不出现；并且尽可能多地匹配
        pattern.oneOrMore().greedy();               // 匹配事件出现1次或多次，并且尽可能多地匹配
        pattern.oneOrMore().optional();             // 匹配事件出现1次或多次，或者不出现
        pattern.oneOrMore().optional().greedy();    // 匹配事件出现1次或多次，或者不出现；并且尽可能多地匹配
        pattern.timesOrMore(2);                     // 匹配事件出现2次或多次
        pattern.timesOrMore(2).greedy();            // 匹配事件出现2次或多次，并且尽可能多地匹配
        pattern.timesOrMore(2).optional();          // 匹配事件出现2次或多次，或者不出现
        pattern.timesOrMore(2).optional().greedy(); // 匹配事件出现2次或多次，或者不出现；并且尽可能多地匹配

        // 1.2 测试规则量词
        pattern.subtype(SubEvent.class);  // 为当前模式增加子类型限制条件：这里SubEvent是流中数据类型Event的子类型。这时，只有当事件是SubEvent类型时，才可以满足当前模式pattern的匹配条件。
        pattern.<LoginEvent>begin("first").where(new SimpleCondition<LoginEvent>() { // 简单条件：简单条件只能基于当前事件做判断，能够处理的逻辑比较有限
            public boolean filter(LoginEvent loginEvent) throws Exception {
                return loginEvent.eventType.equals("fail");
            }
        });
        pattern.oneOrMore()
                .where(new IterativeCondition<LoginEvent>() {   // 迭代条件：实际工作中，常常需要将当前事件跟之前的事件做对比，才能判断出要不要接受当前事件。这种需要依靠之前事件来做判断的条件，就叫作“迭代条件”
                    @Override
                    public boolean filter(LoginEvent value, Context<LoginEvent> ctx) throws Exception {
                        // 事件中的user必须以A开头
                        if (!value.userId.startsWith("A")) {
                            return false;
                        }
                        int sum = value.amount;
                        // 获取当前模式之前已经匹配的事件，求所有事件amount之和
                        for (LoginEvent event : ctx.getEventsForPattern("middle")) {
                            sum += event.amount;
                        }
                        // 在总数量小于100时，当前事件满足匹配规则，可以匹配成功
                        return sum < 100;
                    }
                });




        // 2. 将Pattern应用到流上，检测匹配的复杂事件，得到一个PatternStream
        PatternStream<LoginEvent> patternStream = CEP.pattern(stream, pattern);

        // 3. 将匹配到的复杂事件选择出来，然后包装成字符串报警信息输出
        patternStream
                .select(new PatternSelectFunction<LoginEvent, String>() {
                    @Override
                    public String select(Map<String, List<LoginEvent>> map) throws Exception {
                        LoginEvent first = map.get("first").get(0);
                        LoginEvent second = map.get("second").get(0);
                        LoginEvent third = map.get("third").get(0);
                        return first.userId + " 连续三次登录失败！登录时间：" + first.timestamp + ", " + second.timestamp + ", " + third.timestamp;
                    }
                })
                .print("warning");

        env.execute();
    }

}
