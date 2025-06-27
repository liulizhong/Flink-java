package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;

/**
 *      按键分区状态（Keyed State）：分四种，都可设置状态生存时间TTL
 *          1、值状态（ValueState）
 *          2、列表状态（ListState）
 *          3、Map状态（MapState）
 *          4、归约状态（ReducingState）
 */
public class _12_第八章_Keyed_State {

    /**
     * 1、值状态（ValueState）：TODO 求下个vc和上个vc差值大于5的数据
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

        // 值状态（ValueState）:求下个vc和上个vc差值大于5的数据
        sensorKS.process(new KeyedProcessFunction<String, WaterSensor, String>() {
                             // TODO 1.定义状态
                             ValueState<Integer> lastVcState;

                             @Override
                             public void open(Configuration parameters) throws Exception {
                                 super.open(parameters);
                                 // TODO 2.在open方法中，初始化状态
                                 // 状态描述器两个参数：第一个参数，起个名字，不重复；第二个参数，存储的类型
                                 lastVcState = getRuntimeContext().getState(new ValueStateDescriptor<Integer>("lastVcState", Types.INT));
                             }

                             @Override
                             public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {
                                 //lastVcState.value();  // 取出 本组 值状态 的数据
                                 //lastVcState.update(); // 更新 本组 值状态 的数据
                                 //lastVcState.clear();  // 清除 本组 值状态 的数据

                                 // 1. 取出上一条数据的水位值(Integer默认值是null，判断)
                                 int lastVc = lastVcState.value() == null ? 0 : lastVcState.value();
                                 // 2. 求差值的绝对值，判断是否超过10
                                 Integer vc = value.getVc();
                                 if (Math.abs(vc - lastVc) > 5) {
                                     out.collect("传感器=" + value.getId() + "==>当前水位值=" + vc + ",与上一条水位值=" + lastVc + ",相差超过5！！！！");
                                 }
                                 // 3. 更新状态里的水位值
                                 lastVcState.update(vc);
                             }
                         }
                )
                .print();
        env.execute();
    }


    /**
     * 2、列表状态（ListState）： TODO 统计水位线出现次数的前top3
     * ---算子状态中， list 与 unionlist的区别：  并行度改变时，怎么重新分配状态
     * ------1、list状态：  轮询均分 给 新的 并行子任务
     * ------2、unionlist状态： 原先的多个子任务的状态，合并成一份完整的。 会把 完整的列表 广播给 新的并行子任务 （每人一份完整的）
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
                new WaterSensor("sensor_1", 2L, 15),
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

        // 列表状态（ListState）
        sensorKS.process(new KeyedProcessFunction<String, WaterSensor, String>() {
                             // 创建列表状态
                             ListState<Integer> vcListState;

                             @Override
                             public void open(Configuration parameters) throws Exception {
                                 super.open(parameters);
                                 //初始化列表状态
                                 vcListState = getRuntimeContext().getListState(new ListStateDescriptor<Integer>("vcListState", Types.INT));
                             }

                             @Override
                             public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {
                                 // 1.来一条，存到list状态里
                                 vcListState.add(value.getVc());
                                 // 2.从list状态拿出来(Iterable)， 拷贝到一个List中，排序， 只留3个最大的
                                 Iterable<Integer> vcListIt = vcListState.get();
                                 // 2.1 拷贝到List中
                                 ArrayList<Integer> vcList = new ArrayList<>();
                                 for (Integer vc : vcListIt) {
                                     vcList.add(vc);
                                 }
                                 // 2.2 对List进行降序排序
                                 vcList.sort((o1, o2) -> o2 - o1);
                                 // 2.3 只保留最大的3个(list中的个数一定是连续变大，一超过3就立即清理即可)
                                 if (vcList.size() > 3) {
                                     // 将最后一个元素清除（第4个）
                                     vcList.remove(3);
                                 }
                                 out.collect("传感器id为" + value.getId() + ",最大的3个水位值=" + vcList.toString());
                                 // 3.更新list状态
                                 vcListState.update(vcList);
//                                vcListState.get();            //取出 list状态 本组的数据，是一个Iterable
//                                vcListState.add();            // 向 list状态 本组 添加一个元素
//                                vcListState.addAll();         // 向 list状态 本组 添加多个元素
//                                vcListState.update();         // 更新 list状态 本组数据（覆盖）
//                                vcListState.clear();          // 清空List状态 本组数据
                             }
                         }
                )
                .print();
        env.execute();
    }


    /**
     * 3、Map状态（MapState）： TODO 统计每种传感器每种水位值出现的次数
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
        // Map状态（MapState）
        sensorKS.process(new KeyedProcessFunction<String, WaterSensor, String>() {
                             //创建MapState
                             MapState<Integer, Integer> vcCountMapState;

                             @Override
                             public void open(Configuration parameters) throws Exception {
                                 super.open(parameters);
                                 //初始化MapState
                                 vcCountMapState = getRuntimeContext().getMapState(new MapStateDescriptor<Integer, Integer>("vcCountMapState", Types.INT, Types.INT));
                             }

                             @Override
                             public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {
                                 // 1.判断是否存在vc对应的key
                                 Integer vc = value.getVc();
                                 if (vcCountMapState.contains(vc)) {
                                     // 1.1 如果包含这个vc的key，直接对value+1
                                     Integer count = vcCountMapState.get(vc);
                                     vcCountMapState.put(vc, ++count);
                                 } else {
                                     // 1.2 如果不包含这个vc的key，初始化put进去
                                     vcCountMapState.put(vc, 1);
                                 }
                                 // 2.遍历Map状态，输出每个k-v的值
                                 StringBuilder outStr = new StringBuilder();
                                 outStr.append("======================================\n");
                                 outStr.append("传感器id为" + value.getId() + "\n");
                                 for (Map.Entry<Integer, Integer> vcCount : vcCountMapState.entries()) {
                                     outStr.append(vcCount.toString() + "\n");
                                 }
                                 outStr.append("======================================\n");
                                 out.collect(outStr.toString());
//                                vcCountMapState.get();          // 对本组的Map状态，根据key，获取value
//                                vcCountMapState.contains();     // 对本组的Map状态，判断key是否存在
//                                vcCountMapState.put(, );        // 对本组的Map状态，添加一个 键值对
//                                vcCountMapState.putAll();  // 对本组的Map状态，添加多个 键值对
//                                vcCountMapState.entries();      // 对本组的Map状态，获取所有键值对
//                                vcCountMapState.keys();         // 对本组的Map状态，获取所有键
//                                vcCountMapState.values();       // 对本组的Map状态，获取所有值
//                                vcCountMapState.remove();   // 对本组的Map状态，根据指定key，移除键值对
//                                vcCountMapState.isEmpty();      // 对本组的Map状态，判断是否为空
//                                vcCountMapState.iterator();     // 对本组的Map状态，获取迭代器
//                                vcCountMapState.clear();        // 对本组的Map状态，清空
                             }
                         }
                )
                .print();
        env.execute();
    }

    /**
     * 4、归约状态（ReducingState）： TODO 计算每种传感器的水位和
     */
    @Test
    public void test4() throws Exception {
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
        // 归约状态（ReducingState）
        sensorKS.process(new KeyedProcessFunction<String, WaterSensor, String>() {
                             ReducingState<Integer> vcSumReducingState;

                             @Override
                             public void open(Configuration parameters) throws Exception {
                                 super.open(parameters);
                                 vcSumReducingState = getRuntimeContext()
                                         .getReducingState(
                                                 new ReducingStateDescriptor<Integer>(
                                                         "vcSumReducingState",
                                                         new ReduceFunction<Integer>() {
                                                             @Override
                                                             public Integer reduce(Integer value1, Integer value2) throws Exception {
                                                                 return value1 + value2;
                                                             }
                                                         },
                                                         Types.INT
                                                 )
                                         );
                             }

                             @Override
                             public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {
                                 // 来一条数据，添加到 reducing状态里
                                 vcSumReducingState.add(value.getVc());
                                 Integer vcSum = vcSumReducingState.get();
                                 out.collect("传感器id为" + value.getId() + ",水位值总和=" + vcSum);
//                                vcSumReducingState.get();   // 对本组的Reducing状态，获取结果
//                                vcSumReducingState.add();   // 对本组的Reducing状态，添加数据
//                                vcSumReducingState.clear(); // 对本组的Reducing状态，清空数据
                             }
                         }
                )
                .print();
        env.execute();
    }

    /**
     * 5、聚合状态（AggregatingState）： TODO  计算每种传感器的平均水位
     */
    @Test
    public void test5() throws Exception {
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
        // 聚合状态（AggregatingState）
        sensorKS.process(new KeyedProcessFunction<String, WaterSensor, String>() {
                             AggregatingState<Integer, Double> vcAvgAggregatingState;
                             @Override
                             public void open(Configuration parameters) throws Exception {
                                 super.open(parameters);
                                 vcAvgAggregatingState = getRuntimeContext()
                                         .getAggregatingState(
                                                 new AggregatingStateDescriptor<Integer, Tuple2<Integer, Integer>, Double>(
                                                         "vcAvgAggregatingState",
                                                         new AggregateFunction<Integer, Tuple2<Integer, Integer>, Double>() {
                                                             @Override
                                                             public Tuple2<Integer, Integer> createAccumulator() {
                                                                 return Tuple2.of(0, 0);
                                                             }
                                                             @Override
                                                             public Tuple2<Integer, Integer> add(Integer value, Tuple2<Integer, Integer> accumulator) {
                                                                 return Tuple2.of(accumulator.f0 + value, accumulator.f1 + 1);
                                                             }
                                                             @Override
                                                             public Double getResult(Tuple2<Integer, Integer> accumulator) {
                                                                 return accumulator.f0 * 1D / accumulator.f1; // “1D”表示转化为double类型，再做除法
                                                             }
                                                             @Override
                                                             public Tuple2<Integer, Integer> merge(Tuple2<Integer, Integer> a, Tuple2<Integer, Integer> b) {
//                                                                return Tuple2.of(a.f0 + b.f0, a.f1 + b.f1); //此处不涉及到不同分组间聚合，但为以防唯一，最好这么写
                                                                return null;
                                                             }
                                                         },
                                                         Types.TUPLE(Types.INT, Types.INT))
                                         );
                             }
                             @Override
                             public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {
                                 // 将 水位值 添加到  聚合状态中
                                 vcAvgAggregatingState.add(value.getVc());
                                 // 从 聚合状态中 获取结果
                                 Double vcAvg = vcAvgAggregatingState.get();
                                 out.collect("传感器id为" + value.getId() + ",平均水位值=" + vcAvg);
//                                vcAvgAggregatingState.get();    // 对 本组的聚合状态 获取结果
//                                vcAvgAggregatingState.add();    // 对 本组的聚合状态 添加数据，会自动进行聚合
//                                vcAvgAggregatingState.clear();  // 对 本组的聚合状态 清空数据
                             }
                         }
                )
                .print();
        env.execute();
    }

    /**
     * 6、状态生存时间（TTL）：TODO 若上个水位线超过5，贼更新状态到TTL
     */
    @Test
    public void test6() throws Exception {
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

        // 值状态（ValueState）:求下个vc和上个vc差值大于5的数据
        sensorKS.process(new KeyedProcessFunction<String, WaterSensor, String>() {
                             ValueState<Integer> lastVcState;
                             @Override
                             public void open(Configuration parameters) throws Exception {
                                 super.open(parameters);
                                 // TODO 1.创建 StateTtlConfig
                                 StateTtlConfig stateTtlConfig = StateTtlConfig
                                         .newBuilder(Time.seconds(5)) // 过期时间5s
//                                        .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite) // 状态 “创建、写入”（更新） 更新过期时间
                                         .setUpdateType(StateTtlConfig.UpdateType.OnReadAndWrite)    // 状态 “读取、创建、写入”（更新） 更新过期时间
                                         .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired) // 不返回过期的状态值
                                         .build();
                                 // TODO 2.状态描述器 启用 TTL
                                 ValueStateDescriptor<Integer> stateDescriptor = new ValueStateDescriptor<>("lastVcState", Types.INT);
                                 stateDescriptor.enableTimeToLive(stateTtlConfig);
                                 this.lastVcState = getRuntimeContext().getState(stateDescriptor);
                             }
                             @Override
                             public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {
                                 // 先获取状态值，打印 ==》 读取状态
                                 Integer lastVc = lastVcState.value();
                                 out.collect("key=" + value.getId() + ",状态值=" + lastVc);
                                 // 如果水位大于10，更新状态值 ===》 写入状态
                                 if (value.getVc() > 5) {
                                     lastVcState.update(value.getVc());
                                 }
                             }
                         }
                )
                .print();
        env.execute();
    }
}
