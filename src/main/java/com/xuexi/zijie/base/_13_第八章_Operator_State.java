package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.datastream.*;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.junit.Test;

import java.time.Duration;

import static sun.misc.Version.print;

/**
 * 算子状态（Operator State）：分三种
 * 1、列表状态（ListState）
 * 2、联合列表状态（UnionListState）
 * 3、广播状态（BroadcastState）
 */
public class _13_第八章_Operator_State {
    /**
     * 1、列表状态（ListState）& 联合列表状态（UnionListState）
     *      TODO 在map算子中计算数据的个数,并要求CheckpointedFunction保存算子状态
     *          list 与 unionlist的区别：  并行度改变时，怎么重新分配状态
     *              1）list状态：  轮询均分 给 新的 并行子任务
     *              2）unionlist状态： 原先的多个子任务的状态，合并成一份完整的。 会把 完整的列表 广播给 新的并行子任务 （每人一份完整的）
     *              3）代码区别：list是“.getListState”，unionlist是“.getUnionListState”(具体代码见下边)
     */
    @Test
    public void test1() throws Exception {
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
                new WaterSensor("sensor_1", 2L, 15),
                new WaterSensor("sensor_2", 8L, 1),
                new WaterSensor("sensor_2", 11L, 1),
                new WaterSensor("sensor_2", 10L, 1)
        );
        // 定义Watermark策略
        stream.map(new MyCountMapFunction())
                .print();
        env.execute();
    }

    // TODO 1.实现 CheckpointedFunction 接口
    public class MyCountMapFunction implements MapFunction<WaterSensor, Long>, CheckpointedFunction {
        private Long count = 0L;
        private ListState<Long> state;

        @Override
        public Long map(WaterSensor value) throws Exception {
            return ++count;
        }

        /**
         * TODO 2.本地变量持久化：将 本地变量 拷贝到 算子状态中,开启checkpoint时才会调用
         *
         * @param context
         * @throws Exception
         */
        @Override
        public void snapshotState(FunctionSnapshotContext context) throws Exception {
            System.out.println("snapshotState...");
            // 2.1 清空算子状态
            state.clear();
            // 2.2 将 本地变量 添加到 算子状态 中
            state.add(count);
        }

        /**
         * TODO 3.初始化本地变量：程序启动和恢复时， 从状态中 把数据添加到 本地变量，每个子任务调用一次
         *
         * @param context
         * @throws Exception
         */
        @Override
        public void initializeState(FunctionInitializationContext context) throws Exception {
            System.out.println("initializeState...");
            // 3.1 从 上下文 初始化 算子状态
            state = context.getOperatorStateStore()
                    .getListState(new ListStateDescriptor<Long>("state", Types.LONG));
//                           .getUnionListState(new ListStateDescriptor<Long>("union-state", Types.LONG));
            // 3.2 从 算子状态中 把数据 拷贝到 本地变量
            if (context.isRestored()) {
                for (Long c : state.get()) {
                    count += c;
                }
            }
        }
    }


    /**
     * 2、广播状态（BroadcastState） TODO 水位超过指定的阈值发送告警，阈值可以动态修改
     */
    @Test
    public void test2() throws Exception {
        // 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 创建beam对象
        DataStreamSource<WaterSensor> sensorDS = env.fromElements(
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
                new WaterSensor("sensor_1", 2L, 15),
                new WaterSensor("sensor_2", 8L, 1),
                new WaterSensor("sensor_2", 11L, 1),
                new WaterSensor("sensor_2", 10L, 1)
        );
        // TODO 1. 将 配置流 广播
        DataStreamSource<String> configDS = env.fromElements("5"); // 配置流（用来广播配置）
        MapStateDescriptor<String, Integer> broadcastMapState = new MapStateDescriptor<>("broadcast-state", Types.STRING, Types.INT);
        BroadcastStream<String> configBS = configDS.broadcast(broadcastMapState);
        // TODO 2.把 数据流 和 广播后的配置流 connect
        BroadcastConnectedStream<WaterSensor, String> sensorBCS = sensorDS.connect(configBS);
        // TODO 3.调用 process
        sensorBCS.process(new BroadcastProcessFunction<WaterSensor, String, String>() {
                    /**
                     * 数据流的处理方法： 数据流 只能 读取 广播状态，不能修改
                     */
                    @Override
                    public void processElement(WaterSensor value, ReadOnlyContext ctx, Collector<String> out) throws Exception {
                        // TODO 5.通过上下文获取广播状态，取出里面的值（只读，不能修改）
                        ReadOnlyBroadcastState<String, Integer> broadcastState = ctx.getBroadcastState(broadcastMapState);
                        Integer threshold = broadcastState.get("threshold");
                        // 判断广播状态里是否有数据，因为刚启动时，可能是数据流的第一条数据先来
                        threshold = (threshold == null ? 0 : threshold);
                        if (value.getVc() > threshold) {
                            out.collect(value + ",水位超过指定的阈值：" + threshold + "!!!");
                        }
                    }

                    /**
                     * 广播后的配置流的处理方法:  只有广播流才能修改 广播状态
                     */
                    @Override
                    public void processBroadcastElement(String value, Context ctx, Collector<String> out) throws Exception {
                        // TODO 4. 通过上下文获取广播状态，往里面写数据
                        BroadcastState<String, Integer> broadcastState = ctx.getBroadcastState(broadcastMapState);
                        broadcastState.put("threshold", Integer.valueOf(value));
                    }
                })
                .print();


        env.execute();
    }


}
