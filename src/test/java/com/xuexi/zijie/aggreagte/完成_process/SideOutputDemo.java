package com.xuexi.zijie.aggreagte.完成_process;

import com.xuexi.zijie.bean.WaterSensor;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.Duration;

/**
 * TODO
 *
 * @author cjp
 * @version 1.0
 */
public class SideOutputDemo {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);


        SingleOutputStreamOperator<WaterSensor> sensorDS = env
                .fromElements(
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
                )
//                .map(new WaterSensorMapFunction())
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner((element, ts) -> element.getTs() * 1000L)
                );
        sensorDS.print("第一步:");

        OutputTag<String> warnTag = new OutputTag<>("warn", Types.STRING);
        SingleOutputStreamOperator<WaterSensor> process = sensorDS.keyBy(sensor -> sensor.getId())
                .process(
                        new KeyedProcessFunction<String, WaterSensor, WaterSensor>() {
                            @Override
                            public void processElement(WaterSensor value, Context ctx, Collector<WaterSensor> out) throws Exception {
                                // 使用侧输出流告警
                                if (value.getVc() > 10) {
                                    ctx.output(warnTag, "当前水位=" + value.getVc() + ",大于阈值10！！！");
                                }
                                // 主流正常 发送数据
                                out.collect(value);
                            }
                        }
                );

        process.print("主流");
        process.getSideOutput(warnTag).printToErr("warn");


        env.execute();
    }
}
