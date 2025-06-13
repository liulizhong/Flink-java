package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Test;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.ZoneId;

public class _6_第五章_sink {

    // 【1】SinkFile
    @Test
    public void test1() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        // 2. 必须开启checkpoint，否则一直都是 .inprogress
        env.enableCheckpointing(2000, CheckpointingMode.EXACTLY_ONCE);
        // 4. 数据生成器
        DataGeneratorSource<String> dataGeneratorSource = new DataGeneratorSource<>(
                new GeneratorFunction<Long, String>() {
                    @Override
                    public String map(Long value) throws Exception {
                        return "Number:" + value;
                    }
                },
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(1),
                Types.STRING
        );
        // 5. 生成数据流
        DataStreamSource<String> dataGen = env.fromSource(dataGeneratorSource, WatermarkStrategy.noWatermarks(), "data-generator");
        // 6. 输出到文件系统
        FileSink<String> fieSink = FileSink
                // 输出行式存储的文件，指定路径、指定编码
                .<String>forRowFormat(new Path("/Users/bytedance/Documents/sortware/workhouse/Flink-java/src/main/resources/output"), new SimpleStringEncoder<>("UTF-8"))
                // 输出文件的一些配置： 文件名的前缀、后缀
                .withOutputFileConfig(OutputFileConfig.builder()
                        .withPartPrefix("zijie")
                        .withPartSuffix(".log")
                        .build()
                )
                // 按照目录分桶：如下，就是每分钟一个目录
                .withBucketAssigner(new DateTimeBucketAssigner<>("yyyy-MM-dd HH:mm", ZoneId.systemDefault()))
                // 文件滚动策略:  1分钟 或 1m、文件大小
                .withRollingPolicy(
                        DefaultRollingPolicy.builder()
                                .withRolloverInterval(Duration.ofSeconds(10)) // ofMinutes(1))
                                .withMaxPartSize(new MemorySize(1024 * 1024))
                                .build()
                ).build();
        // 7. 数据流输出
        dataGen.print("dataGen:");
        dataGen.sinkTo(fieSink);
        // 8. 执行
        env.execute();
    }


    // 【2】SinkKafka
    @Test
    public void test2() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 2. 如果是精准一次，必须开启checkpoint（后续章节介绍）
        env.enableCheckpointing(2000, CheckpointingMode.EXACTLY_ONCE);
        // 3. Socket生成流
        SingleOutputStreamOperator<String> sensorDS = env.socketTextStream("hadoop102", 7777);
        /**
         * 4. Kafka Sink:
         * TODO 注意：如果要使用 精准一次 写入Kafka，需要满足以下条件，缺一不可
         *      1、开启checkpoint（后续介绍）
         *      2、设置事务前缀
         *      3、设置事务超时时间：   checkpoint间隔 <  事务超时时间  < max的15分钟
         */
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                // 指定 kafka 的地址和端口
                .setBootstrapServers("hadoop102:9092,hadoop103:9092,hadoop104:9092")
                // 方法1：指定序列化器：指定Topic名称、具体的序列化
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<String>builder()
                                .setTopic("ws")
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                /** 方法2：
                 * 如果要指定写入kafka的key
                 * 可以自定义序列器：
                 * 1、实现 一个接口，重写 序列化 方法
                 * 2、指定key，转成 字节数组
                 * 3、指定value，转成 字节数组
                 * 4、返回一个 ProducerRecord对象，把key、value放进去
                 */
                .setRecordSerializer(
                        new KafkaRecordSerializationSchema<String>() {
                            @Nullable
                            @Override
                            public ProducerRecord<byte[], byte[]> serialize(String element, KafkaSinkContext context, Long timestamp) {
                                String[] datas = element.split(",");
                                byte[] key = datas[0].getBytes(StandardCharsets.UTF_8);
                                byte[] value = element.getBytes(StandardCharsets.UTF_8);
                                return new ProducerRecord<>("ws", key, value);
                            }
                        }
                )
                // 写到kafka的一致性级别： 精准一次、至少一次
                .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                // 如果是精准一次，必须设置 事务的前缀
                .setTransactionalIdPrefix("zijie-")
                // 如果是精准一次，必须设置 事务超时时间: 大于checkpoint间隔，小于 max 15分钟
                .setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 10 * 60 * 1000 + "")
                .build();
        // 5. 写入Kafka
        sensorDS.sinkTo(kafkaSink);
        // 6. 执行
        env.execute();
    }


    // 【3】SinkMysql
    @Test
    public void test3() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 2. 创建Socket的流
        SingleOutputStreamOperator<WaterSensor> sensorDS = env.socketTextStream("hadoop102", 7777).map(s -> new WaterSensor(s.split(",")[0], Long.valueOf(s.split(",")[1]), Integer.valueOf(s.split(",")[2])));
        /**
         * TODO 写入mysql
         * 1、只能用老的sink写法： addsink
         * 2、JDBCSink的4个参数:
         *    第一个参数： 执行的sql，一般就是 insert into
         *    第二个参数： 预编译sql， 对占位符填充值
         *    第三个参数： 执行选项 ---》 攒批、重试
         *    第四个参数： 连接选项 ---》 url、用户名、密码
         */
        // 3. 创建SinkMysql
        SinkFunction<WaterSensor> jdbcSink = JdbcSink.sink(
                "insert into ws values(?,?,?)",
                new JdbcStatementBuilder<WaterSensor>() {
                    @Override
                    public void accept(PreparedStatement preparedStatement, WaterSensor waterSensor) throws SQLException {
                        //每收到一条WaterSensor，如何去填充占位符
                        preparedStatement.setString(1, waterSensor.getId());
                        preparedStatement.setLong(2, waterSensor.getTs());
                        preparedStatement.setInt(3, waterSensor.getVc());
                    }
                },
                JdbcExecutionOptions.builder()
                        .withMaxRetries(3) // 重试次数
                        .withBatchSize(100) // 批次的大小：条数
                        .withBatchIntervalMs(3000) // 批次的时间
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl("jdbc:mysql://hadoop102:3306/test?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8")
                        .withUsername("root")
                        .withPassword("000000")
                        .withConnectionCheckTimeoutSeconds(60) // 重试的超时时间
                        .build()
        );
        // 4. 写出到jsbc
        sensorDS.addSink(jdbcSink);
        // 5. 执行
        env.execute();
    }


    // 【4】自定义Sink
    @Test
    public void test4() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 2. 生成Socket流
        SingleOutputStreamOperator<String> sensorDS = env.socketTextStream("hadoop102", 7777);
        // 3. 自定义Sink
        sensorDS.addSink(new RichSinkFunction<String>() {
            /**
             * sink的核心逻辑，写出的逻辑就写在这个方法里
             * @param value
             * @param context
             * @throws Exception
             */
            @Override
            public void invoke(String value, Context context) throws Exception {
                super.invoke(value, context);
                // 写出逻辑
                // 这个方法是 来一条数据，调用一次,所以不要在这里创建 连接对象
            }

            @Override
            public void open(Configuration parameters) throws Exception {
                super.open(parameters);
                // 在这里 创建连接
                // conn = new xxxx
            }

            @Override
            public void close() throws Exception {
                super.close();
                // 做一些清理、销毁连接
            }
        });
        // 4. 执行
        env.execute();
    }
}













