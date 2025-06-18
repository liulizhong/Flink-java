package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.Partitioner;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.junit.Test;

import java.time.Duration;

public class _8_第六章_watermark迟到处理 {

    // 【1】有序流中内置水位线设置
    @Test
    public void test1() throws Exception {
        // 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 创建beam对象
        DataStreamSource<WaterSensor> stream = env.fromElements(
                new WaterSensor("sensor_1", 1L, 7),
                new WaterSensor("sensor_1", 2L, 6),
                new WaterSensor("sensor_1", 3L, 5),
                new WaterSensor("sensor_1", 4L, 4),
                new WaterSensor("sensor_2", 5L, 3),
                new WaterSensor("sensor_2", 6L, 2),
                new WaterSensor("sensor_2", 9L, 1),
                new WaterSensor("sensor_2", 7L, 1),
                new WaterSensor("sensor_2", 8L, 1),
                new WaterSensor("sensor_2", 11L, 1),
                new WaterSensor("sensor_2", 10L, 1)
        );

        // 1、自定义分区器：数据%分区数，只输入奇数，都只会去往map的一个子任务
        DataStream<WaterSensor> streamPartition = stream.partitionCustom(new Partitioner<WaterSensor>() {
            @Override
            public int partition(WaterSensor waterSensor, int numPartitions) {
                return waterSensor.getVc() % numPartitions;
            }
        }, r -> r);
        SingleOutputStreamOperator<WaterSensor> streamPartitionWater = streamPartition.assignTimestampsAndWatermarks(
                WatermarkStrategy
                        .<WaterSensor>forMonotonousTimestamps()
                        .withTimestampAssigner((r, ts) -> r.getVc() * 1000L)
                        .withIdleness(Duration.ofSeconds(3))  //空闲等待3s
        );


        // 分成两组： 奇数一组，偶数一组 ， 开10s的事件时间滚动窗口
        streamPartitionWater.keyBy(r -> r.getTs() % 2)
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .process(new ProcessWindowFunction<WaterSensor, String, String, TimeWindow>() {
                    @Override
                    public void process(String s, ProcessWindowFunction<WaterSensor, String, String, TimeWindow>.Context context, Iterable<WaterSensor> iterable, Collector<String> collector) throws Exception {
                    }
                })


        env.execute();


    }
}
