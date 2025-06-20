package com.xuexi.zijie.base;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.util.OutputTag;
import org.junit.Test;

public class _10_第六章_双流联结Join {

    // 【1】窗口联结（Window Join）
    @Test
    public void test1() throws Exception {
        // 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 创建流1
        SingleOutputStreamOperator<Tuple2<String, Integer>> stream1 = env
                .fromElements(
                        Tuple2.of("a", 1),
                        Tuple2.of("a", 2),
                        Tuple2.of("b", 3),
                        Tuple2.of("c", 4)
                )
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<Tuple2<String, Integer>>forMonotonousTimestamps()
                                .withTimestampAssigner((value, ts) -> value.f1 * 1000L)
                );
        // 创建流2
        SingleOutputStreamOperator<Tuple3<String, Integer, Integer>> stream2 = env
                .fromElements(
                        Tuple3.of("a", 1, 1),
                        Tuple3.of("a", 11, 1),
                        Tuple3.of("b", 2, 1),
                        Tuple3.of("b", 12, 1),
                        Tuple3.of("c", 14, 1),
                        Tuple3.of("d", 15, 1)
                )
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<Tuple3<String, Integer, Integer>>forMonotonousTimestamps()
                                .withTimestampAssigner((value, ts) -> value.f1 * 1000L)
                );

        /**
         * TODO -window join
         *          1. 落在同一个时间窗口范围内才能匹配
         *          2. 根据keyby的key，来进行匹配关联
         *          3. 只能拿到匹配上的数据，类似有固定时间范围的inner join
         *          4. 必备参数：
         *                  where：stream1的keyby
         *                  equalTo：stream2的keyby
         *                  window：开窗-可随意窗口生成器
         *                  apply：里的join方法是两条流中关联上的数据
         */
        DataStream<String> streamJoin = stream1.join(stream2)
                .where(r1 -> r1.f0)  // stream1的keyby
                .equalTo(r2 -> r2.f0) // stream2的keyby
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .apply(new JoinFunction<Tuple2<String, Integer>, Tuple3<String, Integer, Integer>, String>() {
                    /**
                     * 关联上的数据，调用join方法
                     * @param first  stream1的数据
                     * @param second stream2的数据
                     * @return
                     * @throws Exception
                     */
                    @Override
                    public String join(Tuple2<String, Integer> first, Tuple3<String, Integer, Integer> second) throws Exception {
                        return first + "<----->" + second;
                    }
                });

        streamJoin.print();

        env.execute();
    }

    // 【2】间隔联结（Interval Join）：正常数据 & 迟到数据
    @Test
    public void test2() throws Exception {
        // 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 创建流1
        SingleOutputStreamOperator<Tuple2<String, Integer>> stream1 = env
                .fromElements(
                        Tuple2.of("a", 1),
                        Tuple2.of("a", 2),
                        Tuple2.of("b", 3),
                        Tuple2.of("c", 4)
                )
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<Tuple2<String, Integer>>forMonotonousTimestamps()
                                .withTimestampAssigner((value, ts) -> value.f1 * 1000L)
                );
        // 创建流2
        SingleOutputStreamOperator<Tuple3<String, Integer, Integer>> stream2 = env
                .fromElements(
                        Tuple3.of("a", 1, 1),
                        Tuple3.of("a", 11, 1),
                        Tuple3.of("b", 2, 1),
                        Tuple3.of("b", 12, 1),
                        Tuple3.of("c", 14, 1),
                        Tuple3.of("d", 15, 1)
                )
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<Tuple3<String, Integer, Integer>>forMonotonousTimestamps()
                                .withTimestampAssigner((value, ts) -> value.f1 * 1000L)
                );

        /**
         * TODO -interval join：并可以处理迟到数据
         *          1、只支持事件时间，且需要提前keyby,作为关联的key
         *          2、指定上界、下界的偏移，负号代表时间往前，正号代表时间往后
         *          3、process中，只能处理 join上的数据
         *          4、两条流关联后的watermark，以两条流中最小的为准
         *          5. sideOutputLeftLateData 和 sideOutputRightLateData 设置输出到侧输出流中
         *          5、如果 当前数据的事件时间 < 当前的watermark，就是迟到数据， 主流的process不处理
         *           => between后，可以指定将 左流 或 右流 的迟到数据 放入侧输出流
         */
        // 1. 分别做keyby，key其实就是关联条件
        KeyedStream<Tuple2<String, Integer>, String> streamKB1 = stream1.keyBy(r1 -> r1.f0);
        KeyedStream<Tuple3<String, Integer, Integer>, String> streamKB2 = stream2.keyBy(r2 -> r2.f0);
        // 2. 定义侧输出流
        OutputTag<Tuple2<String, Integer>> ks1LateTag = new OutputTag<>("ks1-late", Types.TUPLE(Types.STRING, Types.INT));
        OutputTag<Tuple3<String, Integer, Integer>> ks2LateTag = new OutputTag<>("ks2-late", Types.TUPLE(Types.STRING, Types.INT, Types.INT));
        // 3. 调用 interval join
        SingleOutputStreamOperator<String> processResult = streamKB1.intervalJoin(streamKB2)
                .between(Time.seconds(-10), Time.seconds(10))
                .sideOutputLeftLateData(ks1LateTag)  // 将 ks1的迟到数据，放入侧输出流【也可以不输出相当于过滤了】
                .sideOutputRightLateData(ks2LateTag) // 将 ks2的迟到数据，放入侧输出流
                .process(
                        new ProcessJoinFunction<Tuple2<String, Integer>, Tuple3<String, Integer, Integer>, String>() {
                            /**
                             * 两条流的数据匹配上，才会调用这个方法
                             * @param left  ks1的数据
                             * @param right ks2的数据
                             * @param ctx   上下文
                             * @param out   采集器
                             * @throws Exception
                             */
                            @Override
                            public void processElement(Tuple2<String, Integer> left, Tuple3<String, Integer, Integer> right, Context ctx, Collector<String> out) throws Exception {
                                // 进入这个方法，是关联上的数据
                                out.collect(left + "<------>" + right);
                            }
                        });
        processResult.print("主流");
        processResult.getSideOutput(ks1LateTag).printToErr("ks1迟到数据");
        processResult.getSideOutput(ks2LateTag).printToErr("ks2迟到数据");
        // 执行
        env.execute();
    }
}
