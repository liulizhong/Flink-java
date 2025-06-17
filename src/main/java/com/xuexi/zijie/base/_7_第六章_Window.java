package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import com.xuexi.zijie.bean.WaterSensorMapFunction;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.*;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.api.windowing.windows.Window;
import org.apache.flink.util.Collector;
import org.junit.Test;

public class _7_第六章_Window {

    // 【1】窗口分发器
    @Test
    public void test1() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 2. 生成Socket流<WaterSensor>
        SingleOutputStreamOperator<WaterSensor> sensorDS = env.socketTextStream("hadoop102", 7777).map(new WaterSensorMapFunction());
        // 3. 窗口操作前先keyBy
        KeyedStream<WaterSensor, String> sensorKS = sensorDS.keyBy(sensor -> sensor.getId());

        // TODO 4～6. 指定 窗口分配器
        //      时间 or 计数？
        //      滚动、滑动、会话、全局？
        // 4.1 全局窗口：没有keyby，窗口内的 所有数据 进入同一个 子任务，并行度只能为1
        sensorDS.windowAll(GlobalWindows.create());
        // 5.1 基于时间的：滚动窗口，窗口长度10s【两种一摸一样】
        sensorKS.window(TumblingProcessingTimeWindows.of(Time.seconds(10)));
        sensorKS.window(TumblingEventTimeWindows.of(Time.seconds(10)));
        // 5.2 基于时间的：滑动窗口，窗口长度10s，滑动步长2s【两种一摸一样】
        sensorKS.window(SlidingProcessingTimeWindows.of(Time.seconds(10), Time.seconds(2)));
        sensorKS.window(SlidingEventTimeWindows.of(Time.seconds(10), Time.seconds(2)));
        // 5.3 基于时间的：会话窗口，超时间隔5s【两种一摸一样】
        sensorKS.window(ProcessingTimeSessionWindows.withGap(Time.seconds(5)));
        sensorKS.window(EventTimeSessionWindows.withGap(Time.seconds(5)));
        // 6.1 基于计数的：滚动窗口，窗口长度=5个元素
        sensorKS.countWindow(5);
        // 6.2 基于计数的：滑动窗口，窗口长度=5个元素，滑动步长=2个元素
        sensorKS.countWindow(5, 2);
        // 6.2 基于计数的：全局窗口，计数窗口的底层就是用的这个，需要自定义的时候才会用
        sensorKS.window(GlobalWindows.create());

        // 7. 执行
        env.execute();
    }


    // 【2】窗口函数
    @Test
    public void test2() throws Exception {
        // 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 创建beam对象
        DataStreamSource<WaterSensor> stream = env.fromElements(
                new WaterSensor("sensor_1", 1L, 7),
                new WaterSensor("sensor_1", 2L, 6),
                new WaterSensor("sensor_2", 3L, 5),
                new WaterSensor("sensor_2", 4L, 4),
                new WaterSensor("sensor_2", 5L, 3),
                new WaterSensor("sensor_3", 6L, 2),
                new WaterSensor("sensor_3", 7L, 1)
        );
        // 按key聚合操作
        KeyedStream<WaterSensor, String> sensorDS = stream.keyBy(r -> r.getId());
        // 窗口分发器-滚动事件时间窗口
        WindowedStream<WaterSensor, String, GlobalWindow> sensorWS = sensorDS.countWindow(2);
        WindowedStream<WaterSensor, String, TimeWindow> sensorWK = sensorDS.window(TumblingProcessingTimeWindows.of(Time.seconds(10)));
        // 1.1 增量聚合函数：归约函数（ReduceFunction）
        SingleOutputStreamOperator<WaterSensor> reduce = sensorWS.reduce(new ReduceFunction<WaterSensor>() {
            @Override
            public WaterSensor reduce(WaterSensor value1, WaterSensor value2) throws Exception {
                System.out.println("调用reduce方法，之前的结果:" + value1 + ",现在来的数据:" + value2);
                return new WaterSensor(value1.getId(), System.currentTimeMillis(), value1.getVc() + value2.getVc());
            }
        });
        reduce.print("ReduceFunction::");
        // 1.2 增量聚合函数：聚合函数（AggregateFunction）[相对于ReduceFunction更灵活，可以修改输出类型了]
        SingleOutputStreamOperator<String> aggregate = sensorWS.aggregate(new AggregateFunction<WaterSensor, Integer, String>() {
            @Override
            public Integer createAccumulator() {
                System.out.println("创建累加器");
                return 0;
            }

            @Override
            public Integer add(WaterSensor waterSensor, Integer integer) {
                System.out.println("调用add方法,waterSensor=" + waterSensor);
                return integer + waterSensor.getVc();
            }

            @Override
            public String getResult(Integer integer) {
                System.out.println("调用getResult方法");
                return integer.toString();
            }

            @Override
            public Integer merge(Integer integer, Integer acc1) {
                System.out.println("调用merge方法");
                return integer + acc1;
            }
        });
        aggregate.print("AggregateFunction::");

        // 2.1 全窗口函数：通用窗口函数（WindowFunction）【他的功能可以被ProcessWindowFunction全覆盖】
//        sensorWK.apply(new WindowFunction<WaterSensor, String, String, TimeWindow>() {
//            @Override
//            public void apply(String s, TimeWindow window, Iterable<WaterSensor> input, Collector<String> out) throws Exception {
//            }
//        });
        // 2.2 全窗口函数：处理窗口函数（ProcessWindowFunction）
        SingleOutputStreamOperator<String> processRS = sensorWK.process(
                new ProcessWindowFunction<WaterSensor, String, String, TimeWindow>() {
                    /**
                     * @param s  分组的key
                     * @param context 窗口对象
                     * @param elements 存的数据
                     * @param out   采集器
                     * @throws Exception
                     */
                    @Override
                    public void process(String s, Context context, Iterable<WaterSensor> elements, Collector<String> out) throws Exception {
                        long count = elements.spliterator().estimateSize();
                        long windowStartTs = context.window().getStart();
                        long windowEndTs = context.window().getEnd();
                        String windowStart = DateFormatUtils.format(windowStartTs, "yyyy-MM-dd HH:mm:ss.SSS");
                        String windowEnd = DateFormatUtils.format(windowEndTs, "yyyy-MM-dd HH:mm:ss.SSS");
                        out.collect("key=" + s + "的窗口[" + windowStart + "," + windowEnd + ")包含" + count + "条数据===>" + elements.toString());
                    }
                }
        );
        processRS.print();

        // 3.1 增量聚合和全窗口函数 的结合
        /**
         * 一、ReduceFunction与WindowFunction结合
         *      public <R> SingleOutputStreamOperator<R> reduce(ReduceFunction<T> reduceFunction，WindowFunction<T，R，K，W> function)
         *
         * 二、ReduceFunction与ProcessWindowFunction结合
         *      public <R> SingleOutputStreamOperator<R> reduce(ReduceFunction<T> reduceFunction，ProcessWindowFunction<T，R，K，W> function)
         *
         * 三、AggregateFunction与WindowFunction结合
         *      public <ACC，V，R> SingleOutputStreamOperator<R> aggregate(AggregateFunction<T，ACC，V> aggFunction，WindowFunction<V，R，K，W> windowFunction)
         *
         * 四、AggregateFunction与ProcessWindowFunction结合
         *      public <ACC，V，R> SingleOutputStreamOperator<R> aggregate(AggregateFunction<T，ACC，V> aggFunction, ProcessWindowFunction<V，R，K，W> windowFunction)
         *
         * 次个案例是：：“增量聚合 Aggregate + 全窗口 process”
         *     1、增量聚合函数处理数据： 来一条计算一条
         *     2、窗口触发时， 增量聚合的结果（只有一条） 传递给 全窗口函数
         *     3、经过全窗口函数的处理包装后，输出
         * 结合两者的优点：
         *     1、增量聚合： 来一条计算一条，存储中间的计算结果，占用的空间少
         *     2、全窗口函数： 可以通过 上下文 实现灵活的功能
         */
        SingleOutputStreamOperator<String> resultAW = sensorWK.aggregate(
                new AggregateFunction<WaterSensor, Integer, String>() {
                    @Override
                    public Integer createAccumulator() {
                        System.out.println("创建累加器");
                        return 0;
                    }

                    @Override
                    public Integer add(WaterSensor waterSensor, Integer integer) {
                        System.out.println("调用add方法,value=" + integer);
                        return integer + waterSensor.getVc();
                    }

                    @Override
                    public String getResult(Integer integer) {
                        System.out.println("调用getResult方法");
                        return integer.toString();
                    }

                    @Override
                    public Integer merge(Integer integer, Integer acc1) {
                        System.out.println("调用merge方法");
                        return integer + acc1;
                    }
                },
                new ProcessWindowFunction<String, String, String, TimeWindow>() {
                    @Override
                    public void process(String s, ProcessWindowFunction<String, String, String, TimeWindow>.Context context, Iterable<String> iterable, Collector<String> collector) throws Exception {
                        long startTs = context.window().getStart();
                        long endTs = context.window().getEnd();
                        String windowStart = DateFormatUtils.format(startTs, "yyyy-MM-dd HH:mm:ss.SSS");
                        String windowEnd = DateFormatUtils.format(endTs, "yyyy-MM-dd HH:mm:ss.SSS");
                        long count = iterable.spliterator().estimateSize();
                        collector.collect("key=" + s + "的窗口[" + windowStart + "," + windowEnd + ")包含" + count + "条数据===>" + iterable.toString());
                    }
                }
        );
        resultAW.print();

        // 5.1 触发器、移除器： 现成的几个窗口，都有默认的实现，一般不需要自定义
        /**
         *  以 时间类型的 滚动窗口 为例，分析原理：
         TODO 1、窗口什么时候触发 输出？
         时间进展 >= 窗口的最大时间戳（end - 1ms）

         TODO 2、窗口是怎么划分的？
         start= 向下取整，取窗口长度的整数倍
         end = start + 窗口长度

         窗口左闭右开 ==》 属于本窗口的 最大时间戳 = end - 1ms

         TODO 3、窗口的生命周期？
         创建： 属于本窗口的第一条数据来的时候，现new的，放入一个singleton单例的集合中
         销毁（关窗）： 时间进展 >=  窗口的最大时间戳（end - 1ms） + 允许迟到的时间（默认0）

         remainder = (timestamp - offset) % windowSize;
         ·  （13s - 0 ）% 10 = 3
         （27s - 0 ）% 10 = 7
         if (remainder < 0) {
         return timestamp - (remainder + windowSize);
         } else {
         return timestamp - remainder;
         13 -3 = 10
         27 - 7 = 20
         }
         */

        env.execute();
    }
}
