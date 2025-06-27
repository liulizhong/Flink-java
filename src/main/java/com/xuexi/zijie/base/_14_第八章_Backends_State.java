package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import com.xuexi.zijie.bean.WaterSensorMapFunction;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.junit.Test;

import java.time.Duration;

public class _14_第八章_Backends_State {

    /**
     * TODO 状态后端
     * 1、负责管理 本地状态
     * 2、 hashmap
     *          存在 TM的 JVM的堆内存，  读写快，缺点是存不了太多（受限与TaskManager的内存）
     *     rocksdb
     *          存在 TM所在节点的rocksdb数据库，存到磁盘中，  写--序列化，读--反序列化
     *          读写相对慢一些，可以存很大的状态
     * <p>
     * 3、配置方式
     * 1）配置文件 默认值  flink-conf.yaml
     * 2）代码中指定
     * 3）提交参数指定
     * flink run-application -t yarn-application
     * -p 3
     * -Dstate.backend.type=rocksdb
     * -c 全类名
     * jar包
     */
    @Test
    public void test1() throws Exception {
        // 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 1. 使用 hashmap状态后端
        env.setStateBackend(new HashMapStateBackend());
        // 2. 使用 rocksdb状态后端
        env.setStateBackend(new EmbeddedRocksDBStateBackend());
        // 3. 创建beam对象
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
        // 4. 定义Watermark策略
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
        // 5. 按id聚合分组
        KeyedStream<WaterSensor, String> sensorKS = sensorDS.keyBy(sensor -> sensor.getId());
        // 6. 调用process
        sensorKS.process(
                        new KeyedProcessFunction<String, WaterSensor, String>() {
                            ValueState<Integer> lastVcState;

                            @Override
                            public void open(Configuration parameters) throws Exception {
                                super.open(parameters);
                                lastVcState = getRuntimeContext().getState(new ValueStateDescriptor<Integer>("lastVcState", Types.INT));
                            }

                            @Override
                            public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {
                                int lastVc = lastVcState.value() == null ? 0 : lastVcState.value();
                                Integer vc = value.getVc();
                                if (Math.abs(vc - lastVc) > 5) {
                                    out.collect("传感器=" + value.getId() + "==>当前水位值=" + vc + ",与上一条水位值=" + lastVc + ",相差超过5！！！！");
                                }
                                lastVcState.update(vc);
                            }
                        }
                )
                .print();
        env.execute();
    }
}
