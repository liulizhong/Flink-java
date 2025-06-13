package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.Test;

public class _4_第五章_function {
    // 【1】Function
    @Test
    public void test1() throws Exception {
        // 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 创建beam对象
        DataStreamSource<WaterSensor> stream = env.fromElements(
                new WaterSensor("sensor_1", 1L, 1),
                new WaterSensor("sensor_1", 2L, 2),
                new WaterSensor("sensor_2", 2L, 2),
                new WaterSensor("sensor_3", 3L, 3)
        );
        // 1. 方法1：lambda表达式
        stream.filter(waterSensor -> waterSensor.id.equals("sensor_1")).print("lamdba:");
        // 2. 方法2：运用自定义函数FilterFunction
        stream.filter(new FilterFunction<WaterSensor>() {
            @Override
            public boolean filter(WaterSensor waterSensor) throws Exception {
                return waterSensor.id.equals("sensor_1");
            }
        }).print("匿名内部类：");
        // 3.方法3：自定义类实现函数
        stream.filter(new MyFilterFunction("sensor_1")).print("自定义类实现函数：");
        // 执行
        env.execute();
    }

    class MyFilterFunction implements FilterFunction<WaterSensor> {
        private String file_str;

        public MyFilterFunction(String file_str) {
            this.file_str = file_str;
        }

        @Override
        public boolean filter(WaterSensor waterSensor) throws Exception {
            return waterSensor.id.equals("sensor_1");
        }
    }


    // 【2】RichFunction
    @Test
    public void test2() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        // 2. 创建beam对象
        DataStreamSource<Integer> stream = env.fromElements(1, 2, 3, 4);
        // 3. 运用自定义函数FilterFunction
        stream.map(new RichMapFunction<Integer, Integer>() {
            @Override
            public void open(Configuration parameters) throws Exception {
                super.open(parameters);
                System.out.println();
                System.out.println(
                        "子任务编号索引是：" + getRuntimeContext().getIndexOfThisSubtask() + " 的任务的生命周期开始"
                                + "，子任务名称=" + getRuntimeContext().getTaskNameWithSubtasks()
                                + ",调用open()");
            }

            @Override
            public Integer map(Integer integer) throws Exception {
                return integer + 1;
            }

            @Override
            public void close() throws Exception {
                super.close();
                System.out.println(
                        "子任务编号索引是：" + getRuntimeContext().getIndexOfThisSubtask() + " 的任务的生命周期结束"
                                + "，子任务名称=" + getRuntimeContext().getTaskNameWithSubtasks()
                                + ",调用close()");
            }
        }).print();
        // 5. 执行
        env.execute();
    }

}
