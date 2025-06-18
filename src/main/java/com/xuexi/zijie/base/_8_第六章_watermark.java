package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import com.xuexi.zijie.bean.WaterSensorMapFunction;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.*;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.junit.Test;

import java.io.Serializable;
import java.time.Duration;


/**
 * TODO 内置Watermark的生成原理
 * 1、都是周期性生成的： 默认200ms
 * 2、有序流：  watermark = 当前最大的事件时间 - 1ms
 * 3、乱序流：  watermark = 当前最大的事件时间 - 延迟时间 - 1ms
 * 4、自定义水位线
 * 5、断点式水位线生成器（Punctuated Generator）
 *          断点式生成器会不停地检测onEvent()中的事件，当发现带有水位线信息的事件时，就立即发出水位线。我们把发射水位线的逻辑写在onEvent方法当中即可。
 * 6、在数据源中发送水位线：【在自定义数据源中生成水位线和在程序中使用assignTimestampsAndWatermarks方法生成水位线二者只能取其一】
 *          env.fromSource(kafkaSource, WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(3)), "kafkasource")
 */
public class _8_第六章_watermark {

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

        // TODO 1.定义Watermark策略
        WatermarkStrategy<WaterSensor> watermarkStrategy = WatermarkStrategy
                // ⭐1.1 指定watermark生成：升序的watermark，没有等待时间
                .<WaterSensor>forMonotonousTimestamps()
                // ⭐1.2 指定 时间戳分配器，从数据中提取
                .withTimestampAssigner(new SerializableTimestampAssigner<WaterSensor>() {
                    @Override
                    public long extractTimestamp(WaterSensor element, long recordTimestamp) {
                        // 返回的时间戳，要 毫秒
                        System.out.println("数据=" + element + ",recordTs=" + recordTimestamp);
                        return element.getTs() * 1000L;
                    }
                });

        // TODO 2. 指定 watermark策略
        SingleOutputStreamOperator<WaterSensor> streamWithWatermark = stream.assignTimestampsAndWatermarks(watermarkStrategy);

        // 按key聚合操作,并建窗口
        WindowedStream<WaterSensor, String, TimeWindow> streamWindow = streamWithWatermark.keyBy(r -> r.getId()).window(TumblingEventTimeWindows.of(Time.seconds(10)));

        // TODO 3.使用 事件时间语义 的窗口
        streamWindow.process(
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
        ).print("输出结果：");
        env.execute();
    }


    // 【2】乱序流中内置水位线设置【和test1中不同的只有：⭐⭐⭐forBoundedOutOfOrderness⭐⭐⭐】
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
                new WaterSensor("sensor_1", 4L, 4),
                new WaterSensor("sensor_2", 5L, 3),
                new WaterSensor("sensor_2", 6L, 2),
                new WaterSensor("sensor_2", 9L, 1),
                new WaterSensor("sensor_2", 7L, 1),
                new WaterSensor("sensor_2", 8L, 1),
                new WaterSensor("sensor_2", 11L, 1),
                new WaterSensor("sensor_2", 10L, 1)
        );

        // TODO 1.定义Watermark策略
        WatermarkStrategy<WaterSensor> watermarkStrategy = WatermarkStrategy
                // ⭐1.1 指定watermark生成：乱序的，等待3s
                .<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                // ⭐1.2 指定 时间戳分配器，从数据中提取
                .withTimestampAssigner(
                        (element, recordTimestamp) -> {
                            // 返回的时间戳，要 毫秒
                            System.out.println("数据=" + element + ",recordTs=" + recordTimestamp);
                            return element.getTs() * 1000L;
                        });

        // TODO 2. 指定 watermark策略
        SingleOutputStreamOperator<WaterSensor> streamWithWatermark = stream.assignTimestampsAndWatermarks(watermarkStrategy);

        // 按key聚合操作,并建窗口
        WindowedStream<WaterSensor, String, TimeWindow> streamWindow = streamWithWatermark.keyBy(r -> r.getId()).window(TumblingEventTimeWindows.of(Time.seconds(10)));


        // TODO 3.使用 事件时间语义 的窗口
        streamWindow.process(
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
        ).print("输出结果：");

        env.execute();
    }


    // 【3】自定义水位线生成器
    @Test
    public void test3() throws Exception {
        // 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.getConfig().setAutoWatermarkInterval(2000); // 方法onPeriodicEmit中的“output.emitWatermark”是调用发出水位线的系统方法，周期是默认200毫秒一次，这里改成了2秒
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
//        stream.assignTimestampsAndWatermarks(new MyPeriodWatermarkGenerator(4000L)).print();
        SingleOutputStreamOperator<WaterSensor> waterSensorSingleOutputStreamOperator = stream.assignTimestampsAndWatermarks(WatermarkStrategy
                .<WaterSensor>forGenerator(ctx -> new NewMyPeriodWatermarkGenerator<>(3000L))
                .withTimestampAssigner(
                        (element, recordTimestamp) -> {
                            return element.getTs() * 1000L;
                        })
        );
        waterSensorSingleOutputStreamOperator.print();
        env.execute();


    }

    public static class NewMyPeriodWatermarkGenerator<WaterSensor> implements WatermarkGenerator<WaterSensor> {

        // 乱序等待时间
        private long delayTs;
        // 用来保存 当前为止 最大的事件时间
        private long maxTs;

        public NewMyPeriodWatermarkGenerator(long delayTs) {
            this.delayTs = delayTs;
            this.maxTs = Long.MIN_VALUE + this.delayTs + 1;
        }

        /**
         * 每条数据来，都会调用一次： 用来提取最大的事件时间，保存下来
         *
         * @param event
         * @param eventTimestamp 提取到的数据的 事件时间
         * @param output
         */
        @Override
        public void onEvent(WaterSensor event, long eventTimestamp, WatermarkOutput output) {
            maxTs = Math.max(maxTs, eventTimestamp);
            System.out.println("调用onEvent方法，获取目前为止的最大时间戳=" + maxTs);
        }

        /**
         * 周期性调用： 发射 watermark
         *
         * @param output
         */
        @Override
        public void onPeriodicEmit(WatermarkOutput output) {
            output.emitWatermark(new Watermark(maxTs - delayTs - 1));
            System.out.println("调用onPeriodicEmit方法，生成watermark=" + (maxTs - delayTs - 1));
        }
    }
}
