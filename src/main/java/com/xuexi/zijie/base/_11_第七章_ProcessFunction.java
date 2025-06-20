package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import com.xuexi.zijie.bean.WaterSensorMapFunction;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.TimerService;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.kafka.common.protocol.types.Field;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;


/**
 * TODO 定时器
 * 1、keyed才有
 * 2、事件时间定时器，通过watermark来触发的
 *    watermark >= 注册的时间
 *    注意： watermark = 当前最大事件时间 - 等待时间 -1ms， 因为 -1ms，所以会推迟一条数据
 *        比如， 5s的定时器，
 *        如果 等待=3s， watermark = 8s - 3s -1ms = 4999ms,不会触发5s的定时器
 *        需要 watermark = 9s -3s -1ms = 5999ms ，才能去触发 5s的定时器
 * 3、在process中获取当前watermark，显示的是上一次的watermark
 *    =》因为process还没接收到这条数据对应生成的新watermark
 */
public class _11_第七章_ProcessFunction {


    // 【1】处理函数：KeyedProcessFunction
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

        // 定义Watermark策略
        SingleOutputStreamOperator<WaterSensor> sensorDS = stream.assignTimestampsAndWatermarks(
                WatermarkStrategy
                        // 设置乱序流中的水位线，水印推出3秒
                        .<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                        // 设置水位线的事件事件：元素中的Ts字段*1000
                        .withTimestampAssigner(new SerializableTimestampAssigner<WaterSensor>() {
                            @Override
                            public long extractTimestamp(WaterSensor waterSensor, long l) {
                                return waterSensor.getTs() * 1000L;
                            }
                        })
        );
        // 按id聚合分组
        KeyedStream<WaterSensor, String> sensorKS = sensorDS.keyBy(sensor -> sensor.getId());

        // 一、处理函数：KeyedProcessFunction案例
        SingleOutputStreamOperator<String> process = sensorKS.process(new KeyedProcessFunction<String, WaterSensor, String>() {
            /**
             * 来一条数据调用一次
             * @param waterSensor
             * @param context
             * @param collector
             * @throws
             */
            @Override
            public void processElement(WaterSensor waterSensor, KeyedProcessFunction<String, WaterSensor, String>.Context context, Collector<String> collector) throws Exception {
                // 1. 获取当前数据的key
                String exentKey = context.getCurrentKey();

                // 2. 提取数据中的事件事件
                Long exentTime = context.timestamp();

                // 3. 定时器：事件事件
                TimerService timerService1 = context.timerService();
                timerService1.registerEventTimeTimer(5000L); // 定时器，设置为5秒
                // 打印这些时间
                System.out.println("当前key=" + exentKey + ",事件时间=" + exentTime + ",注册了一个5s的定时器");

                // 4. 定时器：处理事件
                TimerService timerService2 = context.timerService();
                long currentTs = timerService2.currentProcessingTime();
                timerService2.registerProcessingTimeTimer(currentTs + 5000L); //系统时间+5秒触发
                System.out.println("当前key=" + exentKey + ",处理时间=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(1750384276196L)) + ",注册了一个5s后的定时器");


                // 5. 获取 process的 当前watermark
                long currentWatermark = timerService1.currentWatermark();
                System.out.println("当前数据=" + waterSensor + ",当前watermark=" + currentWatermark);

                // 6. 获取当前时间进展： 处理时间-当前系统时间，  事件时间-当前watermark
                long currentTs2 = timerService2.currentProcessingTime();
                long wm = timerService2.currentWatermark();

                // 7. 删除定时器： 处理时间、事件时间
                timerService1.deleteEventTimeTimer(5000L);
                timerService2.deleteProcessingTimeTimer(5000L);
            }

            /**
             * TODO 2.时间进展到定时器注册的时间，调用该方法
             * @param timestamp 当前时间进展，就是定时器被触发时的时间
             * @param ctx       上下文
             * @param out       采集器
             * @throws Exception
             */
            @Override
            public void onTimer(long timestamp, KeyedProcessFunction<String, WaterSensor, String>.OnTimerContext ctx, Collector<String> out) throws Exception {
                super.onTimer(timestamp, ctx, out);
                String currentKey = ctx.getCurrentKey();
                System.out.println("onTimer方法：：key=" + currentKey + "现在时间是" + timestamp + "定时器触发");
            }
        });
        process.print();
        env.execute();
    }


    /**
     * 【2】处理函数：ProcessWindowFunction
     *   TODO：统计最近10秒钟内出现次数最多的两个水位，并且每5秒钟更新一次
     *      思路一： 所有数据到一起， 用hashmap存， key=vc，value=count值
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
                new WaterSensor("sensor_1", 2L, 6),
                new WaterSensor("sensor_1", 2L, 6),
                new WaterSensor("sensor_1", 3L, 5),
                new WaterSensor("sensor_1", 3L, 5),
                new WaterSensor("sensor_1", 4L, 4),
                new WaterSensor("sensor_2", 5L, 3),
                new WaterSensor("sensor_2", 6L, 2),
                new WaterSensor("sensor_2", 6L, 2),
                new WaterSensor("sensor_2", 9L, 1),
                new WaterSensor("sensor_2", 7L, 1),
                new WaterSensor("sensor_2", 8L, 1),
                new WaterSensor("sensor_2", 11L, 1),
                new WaterSensor("sensor_2", 10L, 1)
        );
        // 定义Watermark策略
        SingleOutputStreamOperator<WaterSensor> sensorDS = stream.assignTimestampsAndWatermarks(
                WatermarkStrategy
                        // 设置乱序流中的水位线，水印推出3秒
                        .<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                        // 设置水位线的事件事件：元素中的Ts字段*1000
                        .withTimestampAssigner(new SerializableTimestampAssigner<WaterSensor>() {
                            @Override
                            public long extractTimestamp(WaterSensor waterSensor, long l) {
                                return waterSensor.getTs() * 1000L;
                            }
                        })
        );
        // 直接用windowAll处理
        sensorDS.windowAll(SlidingEventTimeWindows.of(Time.seconds(10), Time.seconds(5)))
                .process(new ProcessAllWindowFunction<WaterSensor, String, TimeWindow>() {
                    @Override
                    public void process(ProcessAllWindowFunction<WaterSensor, String, TimeWindow>.Context context, Iterable<WaterSensor> iterable, Collector<String> collector) throws Exception {
                        // 1. 初始化map，存vc和count
                        HashMap<Integer, Integer> vcCountMap = new HashMap<>();
                        // 2. 遍历数据，统计vc的count
                        for (WaterSensor waterSensor : iterable) {
                            Integer vc = waterSensor.getVc();
                            if (vcCountMap.containsKey(vc)) {
                                vcCountMap.put(vc, vcCountMap.get(vc) + 1);
                            } else {
                                vcCountMap.put(vc, 1);
                            }
                        }
                        //3. 先对vc的count进行排序：利用List实现排序
                        ArrayList<Tuple2<Integer, Integer>> dates = new ArrayList<>();
                        for (Integer vc : vcCountMap.keySet()) {
                            dates.add(Tuple2.of(vc, vcCountMap.get(vc)));
                        }
                        dates.sort(new Comparator<Tuple2<Integer, Integer>>() {
                            @Override
                            public int compare(Tuple2<Integer, Integer> o1, Tuple2<Integer, Integer> o2) {
                                return o1.f1 - o2.f1;
                            }
                        });
                        //4. 取出最大的前2名
                        StringBuilder outStr = new StringBuilder();
                        for (int i = 0; i < Math.min(2, dates.size()); i++) {
                            Tuple2<Integer, Integer> vcCount = dates.get(i);
                            outStr.append("Top" + (i + 1) + "\n");
                            outStr.append("vc=" + vcCount.f0 + "\n");
                            outStr.append("count=" + vcCount.f1 + "\n");
                            outStr.append("窗口结束时间=" + DateFormatUtils.format(context.window().getEnd(), "yyyy-MM-dd HH:mm:ss.SSS") + "\n");
                            outStr.append("================================\n");
                        }
                        collector.collect(outStr.toString());
                    }
                }).print("allWindow处理：");
    }

    /**
     * 【3】处理函数：KeyedProcessFunction
     *   TODO：统计最近10秒钟内出现次数最多的两个水位，并且每5秒钟更新一次
     *      思路二： 使用 KeyedProcessFunction实现
     *          1、按照vc做keyby，开窗，分别count
     *              ==》 增量聚合，计算 count
     *              ==》 全窗口，对计算结果 count值封装 ，  带上 窗口结束时间的 标签
     *              ==》 为了让同一个窗口时间范围的计算结果到一起去
     *          2、对同一个窗口范围的count值进行处理： 排序、取前N个
     *              =》 按照 windowEnd做keyby
     *              =》 使用process， 来一条调用一次，需要先存，分开存，用HashMap,key=windowEnd,value=List
     *          =   》 使用定时器，对 存起来的结果 进行 排序、取前N个
     */
    @Test
    public void test3() throws Exception {
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
        // 定义Watermark策略
        SingleOutputStreamOperator<WaterSensor> sensorDS = stream.assignTimestampsAndWatermarks(
                WatermarkStrategy
                        // 设置乱序流中的水位线，水印推出3秒
                        .<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                        // 设置水位线的事件事件：元素中的Ts字段*1000
                        .withTimestampAssigner(new SerializableTimestampAssigner<WaterSensor>() {
                            @Override
                            public long extractTimestamp(WaterSensor waterSensor, long l) {
                                return waterSensor.getTs() * 1000L;
                            }
                        })
        );
        // 按id聚合分组
        KeyedStream<WaterSensor, String> sensorKS = sensorDS.keyBy(sensor -> sensor.getId());

        // 一、处理函数：KeyedProcessFunction案例
    }
}
