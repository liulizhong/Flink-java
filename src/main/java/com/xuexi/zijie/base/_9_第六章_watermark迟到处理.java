package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.Partitioner;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.junit.Test;

import java.time.Duration;

public class _9_第六章_watermark迟到处理 {

    // 【1】自定义分区器，实现奇数一个分区，偶数一个分区
    @Test
    public void test1() throws Exception {
        // 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 创建beam对象
        DataStreamSource<Integer> stream = env.fromElements(1, 2, 3, 4, 5, 6);

        // 1、自定义分区器：数据%分区数，只输入奇数，都只会去往map的一个子任务
        DataStream<Integer> streamPartition = stream.partitionCustom(new Partitioner<Integer>() {
            @Override
            public int partition(Integer integer, int numPartitions) {
                return integer % numPartitions;
            }
        }, r -> r);
        SingleOutputStreamOperator<Integer> streamPartitionWater = streamPartition.assignTimestampsAndWatermarks(
                WatermarkStrategy
                        .<Integer>forMonotonousTimestamps()
                        .withTimestampAssigner((r, ts) -> r * 1000L)
                        .withIdleness(Duration.ofSeconds(3))  //空闲等待3s
        );
        // 2、分成两组： 奇数一组，偶数一组 ， 开10s的事件时间滚动窗口
        streamPartitionWater.keyBy(r -> r % 2)
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .process(new ProcessWindowFunction<Integer, String, Integer, TimeWindow>() {
                    @Override
                    public void process(Integer integer, Context context, Iterable<Integer> elements, Collector<String> out) throws Exception {
                        long startTs = context.window().getStart();
                        long endTs = context.window().getEnd();
                        String windowStart = DateFormatUtils.format(startTs, "yyyy-MM-dd HH:mm:ss.SSS");
                        String windowEnd = DateFormatUtils.format(endTs, "yyyy-MM-dd HH:mm:ss.SSS");
                        long count = elements.spliterator().estimateSize();
                        out.collect("key=" + integer + "的窗口[" + windowStart + "," + windowEnd + ")包含" + count + "条数据===>" + elements.toString());
                    }
                })
                .print();
        // 执行
        env.execute();
    }

    /**
     * 【2】延迟数据处理
     *          1️⃣水印推出3秒：.<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3))
     *          2️⃣设置窗口延迟关闭2秒：.allowedLateness(Time.seconds(2))
     *          3️⃣侧输出流：.sideOutputLateData(lateWS)
     */
    @Test
    public void test2() throws Exception {
        // 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 创建beam对象
        DataStreamSource<WaterSensor> stream = env.fromElements(
                new WaterSensor("sensor_1", 1L, 7),
                new WaterSensor("sensor_1", 2L, 6),
                new WaterSensor("sensor_1", 3L, 5),
                new WaterSensor("sensor_2", 1L, 3),
                new WaterSensor("sensor_2", 2L, 2),
                new WaterSensor("sensor_2", 3L, 1),
                new WaterSensor("sensor_2", 5L, 1),
                new WaterSensor("sensor_2", 10L, 1),
                new WaterSensor("sensor_2", 11L, 1),
                new WaterSensor("sensor_2", 12L, 1),
                new WaterSensor("sensor_2", 13L, 1),
                new WaterSensor("sensor_2", 14L, 1),
                new WaterSensor("sensor_2", 6L, 4),
                new WaterSensor("sensor_2", 4L, 4),
                new WaterSensor("sensor_2", 10L, 1)
        );
        // 1、方法一：水印推出3秒：.<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3))
        WatermarkStrategy<WaterSensor> watermarkStrategy = WatermarkStrategy
                // 设置乱序流中的水位线，水印推出3秒
                .<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                // 设置水位线的事件事件：元素中的Ts字段*1000
                .withTimestampAssigner((element, recordTimestamp) -> element.getTs() * 1000L);
        SingleOutputStreamOperator<WaterSensor> sensorWatermark = stream.assignTimestampsAndWatermarks(watermarkStrategy);

        // 2、方法二：设置窗口延迟关闭2秒：.allowedLateness(Time.seconds(2))
        WindowedStream<WaterSensor, String, TimeWindow>  sensorWatermarkWindow = sensorWatermark.keyBy(sensor -> sensor.getId()).window(TumblingEventTimeWindows.of(Time.seconds(5)));
        WindowedStream<WaterSensor, String, TimeWindow> sensorkWindowLate = sensorWatermarkWindow.allowedLateness(Time.seconds(2));

        // 3、设置侧输出流
        OutputTag<WaterSensor> lateTag = new OutputTag<>("late-data", Types.POJO(WaterSensor.class));
        SingleOutputStreamOperator<String> processResult = sensorkWindowLate.sideOutputLateData(lateTag) // 关窗后的迟到数据，放入侧输出流
                .process(
                        new ProcessWindowFunction<WaterSensor, String, String, TimeWindow>() {
                            @Override
                            public void process(String s, Context context, Iterable<WaterSensor> elements, Collector<String> out) throws Exception {
                                long startTs = context.window().getStart();
                                long endTs = context.window().getEnd();
                                String windowStart = DateFormatUtils.format(startTs, "yyyy-MM-dd HH:mm:ss.SSS");
                                String windowEnd = DateFormatUtils.format(endTs, "yyyy-MM-dd HH:mm:ss.SSS");
                                long count = elements.spliterator().estimateSize();
                                out.collect("key=" + s + "的窗口[" + windowStart + "," + windowEnd + ")包含" + count + "条数据===>" + elements.toString());
                            }
                        }
                );
        // 打印主流
        processResult.print("主流：");
        // 从主流获取侧输出流，打印
        processResult.getSideOutput(lateTag).printToErr("关窗后的迟到数据");

        env.execute();
    }
}
