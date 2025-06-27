package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.Test;

import java.time.Duration;

import static org.apache.flink.streaming.api.environment.ExecutionCheckpointingOptions.ENABLE_CHECKPOINTS_AFTER_TASKS_FINISH;

public class _15_第九章_checkpoint_savepoint {
    /**
     * TODO 检查点算法的总结
     * 1、Barrier对齐： 一个Task 收到 所有上游 同一个编号的 barrier之后，才会对自己的本地状态做 备份
     *      精准一次： 在barrier对齐过程中，barrier后面的数据 阻塞等待（不会越过barrier）
     *      至少一次： 在barrier对齐过程中，先到的barrier，其后面的数据 不阻塞 接着计算
     * 2、非Barrier对齐： 一个Task 收到 第一个 barrier时，就开始 执行备份，能保证 精准一次（flink 1.11出的新算法）
     *      先到的barrier，将 本地状态 备份， 其后面的数据接着计算输出
     *      未到的barrier，其 前面的数据 接着计算输出，同时 也保存到 备份中
     *      最后一个barrier到达 该Task时，这个Task的备份结束
     */
    @Test
    public void test1() throws Exception {
        // 1. TODO 最终检查点：1.15开始，默认是true（在执行环境中配置）
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(new Configuration().set(ENABLE_CHECKPOINTS_AFTER_TASKS_FINISH, false));
        env.setParallelism(1);
        // 2. TODO 开启 Changelog （要求checkpoint的最大并发必须为1，其他参数建议在flink-conf配置文件中去指定）
        env.enableChangelogStateBackend(true);
        // 3. 代码中用到hdfs，需要导入hadoop依赖、指定访问hdfs的用户名
        System.setProperty("HADOOP_USER_NAME", "zijie");
        // 4. TODO 检查点常用配置（barrier对齐）
        // 4.1、启用检查点: 默认是barrier对齐的，周期为5s, 精准一次
        env.enableCheckpointing(5000, CheckpointingMode.EXACTLY_ONCE);
        CheckpointConfig checkpointConfig = env.getCheckpointConfig();
        // 4.2、指定检查点的存储位置
        checkpointConfig.setCheckpointStorage("hdfs://hadoop102:8020/chk");
        // 4.3、checkpoint的超时时间: 默认10分钟
        checkpointConfig.setCheckpointTimeout(60000);
        // 4.4、同时运行中的checkpoint的最大数量
        checkpointConfig.setMaxConcurrentCheckpoints(1);
        // 4.5、最小等待间隔: 上一轮checkpoint结束 到 下一轮checkpoint开始 之间的间隔
        checkpointConfig.setMinPauseBetweenCheckpoints(1000);
        // 4.6、取消作业时，checkpoint的数据 是否保留在外部系统
            // DELETE_ON_CANCELLATION:主动cancel时，删除存在外部系统的chk-xx目录 （如果是程序突然挂掉，不会删）
            // RETAIN_ON_CANCELLATION:主动cancel时，外部系统的chk-xx目录会保存下来
        checkpointConfig.setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        // 4.7、允许 checkpoint 连续失败的次数，默认0--》表示checkpoint一失败，job就挂掉
        checkpointConfig.setTolerableCheckpointFailureNumber(10);

        // 5. TODO 开启 非对齐检查点（barrier非对齐）
        // 5.1、开启的要求： Checkpoint模式必须是精准一次，最大并发必须设为1
        checkpointConfig.enableUnalignedCheckpoints();
        // 5.2、开启非对齐检查点才生效： 默认0，表示一开始就直接用 非对齐的检查点
        // 如果大于0， 一开始用 对齐的检查点（barrier对齐）， 对齐的时间超过这个参数，自动切换成 非对齐检查点（barrier非对齐）
        checkpointConfig.setAlignedCheckpointTimeout(Duration.ofSeconds(1));
        // 6. 测试数据打印
        env.fromElements(1, 2, 3, 5, 4, 7, 6, 5, 4, 1)
                .flatMap(
                        (Integer integer, Collector<Tuple2<Integer, Integer>> out) -> {
                            out.collect(Tuple2.of(integer, 1));
                        }
                )
                .uid("flatmap-wc").name("wc-flatmap") //使用Savepoint时：对于没有设置ID的算子，Flink默认会自动进行设置，所以在重新启动应用后可能会导致ID不同而无法兼容以前的状态。所以为了方便后续的维护，强烈建议在程序中为每一个算子手动指定ID
                .returns(Types.TUPLE(Types.STRING, Types.INT))
                .keyBy(value -> value.f0)
                .sum(1)
                .print();

        env.execute();
    }


    /**
     *  KafkaEOSDemo
     */
    @Test
    public void test2() throws Exception {
        // 获取env环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 代码中用到hdfs，需要导入hadoop依赖、指定访问hdfs的用户名
        System.setProperty("HADOOP_USER_NAME", "atguigu");
        // TODO 1、启用检查点,设置为精准一次
        env.enableCheckpointing(5000, CheckpointingMode.EXACTLY_ONCE);
        CheckpointConfig checkpointConfig = env.getCheckpointConfig();
        checkpointConfig.setCheckpointStorage("hdfs://hadoop102:8020/chk");
        checkpointConfig.setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION); //取消作业时，checkpoint的数据 仍保留在外部系统
        // TODO 2.读取kafka
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers("hadoop102:9092,hadoop103:9092,hadoop104:9092")
                .setGroupId("atguigu")
                .setTopics("topic_1")
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setStartingOffsets(OffsetsInitializer.latest())
                // TODO 作为 下游的消费者，要设置 事务的隔离级别 = 读已提交
                .setProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")
                .build();
        // 获取Kafka数据流
        DataStreamSource<String> kafkasource = env.fromSource(kafkaSource, WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(3)), "kafkasource");
        /**
         * TODO 3.写出到Kafka
         * 精准一次 写入Kafka，需要满足以下条件，缺一不可
         * 1、开启checkpoint
         * 2、sink设置保证级别为 精准一次
         * 3、sink设置事务前缀
         * 4、sink设置事务超时时间： checkpoint间隔 <  事务超时时间  < max的15分钟
         */
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                // 指定 kafka 的地址和端口
                .setBootstrapServers("hadoop102:9092,hadoop103:9092,hadoop104:9092")
                // 指定序列化器：指定Topic名称、具体的序列化
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<String>builder()
                                .setTopic("ws")
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                // TODO 3.1 精准一次,开启 2pc
                .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                // TODO 3.2 精准一次，必须设置 事务的前缀
                .setTransactionalIdPrefix("atguigu-")
                // TODO 3.3 精准一次，必须设置 事务超时时间: 大于checkpoint间隔，小于 max 15分钟
                .setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 10*60*1000+"")
                .build();
        // 写回到Kafka
        kafkasource.sinkTo(kafkaSink);
        env.execute();
    }
}
