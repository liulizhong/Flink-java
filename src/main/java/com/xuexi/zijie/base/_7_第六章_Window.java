package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import com.xuexi.zijie.bean.WaterSensorMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.*;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
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
        // 4 全局窗口：没有keyby，窗口内的 所有数据 进入同一个 子任务，并行度只能为1
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
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 2. 创建beam对象
        DataStreamSource<WaterSensor> stream = env.fromElements(
                new WaterSensor("sensor_1", 1L, 1),
                new WaterSensor("sensor_1", 2L, 2),
                new WaterSensor("sensor_2", 2L, 2),
                new WaterSensor("sensor_2", 2L, 2),
                new WaterSensor("sensor_2", 2L, 2),
                new WaterSensor("sensor_3", 3L, 3)
        );
        // 3.归约函数（ReduceFunction）
        stream.keyBy(r -> r.getId())
                // 设置滚动事件时间窗口
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                .reduce(new ReduceFunction<WaterSensor>() {
                    @Override
                    public WaterSensor reduce(WaterSensor value1, WaterSensor value2) throws Exception {
                        System.out.println("调用reduce方法，之前的结果:"+value1 + ",现在来的数据:"+value2);
                        return new WaterSensor(value1.getId(), System.currentTimeMillis(),value1.getVc()+value2.getVc());
                    }
                })
                .print();
        // 4.

        env.execute();

    }
}
