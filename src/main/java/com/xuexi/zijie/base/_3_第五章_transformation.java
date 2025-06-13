package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.junit.Test;

public class _3_第五章_transformation {

    // TODO【1】trans：map【演示三种方式实现函数功能：1）lamdba表达式 2）匿名内部类 3）自定义函数实现函数借口】
    @Test
    public void test1() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 2. 创建beam对象
        DataStreamSource<WaterSensor> stream = env.fromElements(
                new WaterSensor("sensor_1", 1L, 1),
                new WaterSensor("sensor_2", 2L, 2)
        );

        // 3. 方式一：Lambda表达式
        stream.map(waterSensor -> waterSensor.id).print();
        // 4. 方式二：传入匿名类，实现MapFunction
        stream.map(new MapFunction<WaterSensor, String>() {
            @Override
            public String map(WaterSensor e) throws Exception {
                return e.id;
            }
        }).print();
        // 5. 方式三：传入MapFunction的实现类
        stream.map(new UserMap()).print();
        // 6. 执行
        env.execute();
    }

    class UserMap implements MapFunction<WaterSensor, String> {
        @Override
        public String map(WaterSensor e) throws Exception {
            return e.id;
        }
    }

    // TODO【2】trans：所有算子
    @Test
    public void test2() throws Exception {
        // 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 创建beam对象
        DataStreamSource<WaterSensor> stream = env.fromElements(
                new WaterSensor("sensor_1", 1L, 1),
                new WaterSensor("sensor_1", 2L, 2),
                new WaterSensor("sensor_2", 2L, 2),
                new WaterSensor("sensor_3", 3L, 3)
        );

        // -----------------=============transformation-start==============------------------------
        // 1. map
        stream.map(waterSensor -> waterSensor.id).print();
        // 2. filter
        stream.filter(waterSensor -> waterSensor.id.equals("sensor_1")).print();
        // 3. flatMap
        stream.flatMap(new FlatMapFunction<WaterSensor, String>() {
            @Override
            public void flatMap(WaterSensor value, Collector<String> out) throws Exception {
                if (value.id.equals("sensor_1")) {
                    out.collect(String.valueOf(value.vc));
                } else if (value.id.equals("sensor_2")) {
                    out.collect(String.valueOf(value.ts));
                    out.collect(String.valueOf(value.vc));
                }
            }
        }).print();
        // 4. keyBy
        stream.keyBy(e -> e.id).print();// 类型：KeyedStream<WaterSensor, String>
        // 5.简单聚合： max、min、sum、minBy()、maxBy()、sumBy()
        stream.keyBy(e -> e.id).max("vc").print("max:");// 指定字段名称
        stream.keyBy(e -> e.id).maxBy("vc").print("maxBy:");// 指定字段名称
        // 6. 归约聚合（reduce）
        stream.keyBy(WaterSensor::getId)
                .reduce(new ReduceFunction<WaterSensor>() {
                    @Override
                    public WaterSensor reduce(WaterSensor value1, WaterSensor value2) throws Exception {
                        System.out.println("Demo7_Reduce.reduce");
                        int maxVc = Math.max(value1.getVc(), value2.getVc());
                        //实现max(vc)的效果  取最大值，其他字段以当前组的第一个为主
                        //value1.setVc(maxVc);
                        //实现maxBy(vc)的效果  取当前最大值的所有字段
                        if (value1.getVc() > value2.getVc()) {
                            value1.setVc(maxVc);
                            return value1;
                        } else {
                            value2.setVc(maxVc);
                            return value2;
                        }
                    }
                })
                .print();


        // -----------------=============transformation-end==============------------------------
        // 执行
        env.execute();
    }
}
