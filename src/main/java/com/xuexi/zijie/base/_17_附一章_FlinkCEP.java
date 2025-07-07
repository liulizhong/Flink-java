package com.xuexi.zijie.base;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class _17_附一章_FlinkCEP {

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

    class LoginEvent {
        public String userId;
        public String ipAddress;
        public String eventType;
        public Long timestamp;

        public LoginEvent(String userId, String ipAddress, String eventType, Long timestamp) {
            this.userId = userId;
            this.ipAddress = ipAddress;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }

        public LoginEvent() {
        }

        @Override
        public String toString() {
            return "LoginEvent{" +
                    "userId='" + userId + '\'' +
                    ", ipAddress='" + ipAddress + '\'' +
                    ", eventType='" + eventType + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
