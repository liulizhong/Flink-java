package com.xuexi.zijie.base;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class _2_第五章_env_and_Source {

    //【1】env：创建多种场景env
    @Test
    public void test1() throws Exception {
        // 1、根据上下文获取执行环境：getExecutionEnvironment
        StreamExecutionEnvironment env = StreamExecutionEnvironment
                //.getExecutionEnvironment();  // 自动识别是 远程集群，还是idea本地环境
                .getExecutionEnvironment(new Configuration().set(RestOptions.BIND_PORT, "8082")); // conf对象可以去修改一些参数
        // 2、本地执行环境：createLocalEnvironment
        LocalStreamEnvironment env2 = StreamExecutionEnvironment.createLocalEnvironment();
        // 3、返回集群执行环境
        StreamExecutionEnvironment.createRemoteEnvironment("hadoop102", 8081, "/xxx");
        // 4、指定 批/流 执行模式【flink是流批一体的代码模式，也可强制指定模式，默认是STREAMING流执行模式，BATCH为批】
        // 一般不在代码写死，提交时 参数指定：-Dexecution.runtime-mode=BATCH
        env.setRuntimeMode(RuntimeExecutionMode.BATCH);
        // 5、一系列转化蒜子
        DataStreamSource<String> result = env.readTextFile("input/word.txt");
        result.print();
        // 6、执行
        env.execute();      //阻塞当前线程直到作业结束，一般用于本地测试
        env.executeAsync(); //立即返回，不阻塞线程，一般用于生产环境
        /** TODO 关于execute总结(了解)
         *     1、默认 env.execute()触发一个flink job：
         *          一个main方法可以调用多个execute，但是没意义，指定到第一个就会阻塞住
         *     2、env.executeAsync()，异步触发，不阻塞
         *         => 一个main方法里 executeAsync()个数 = 生成的flink job数
         *     3、思考：
         *         yarn-application 集群，提交一次，集群里会有几个flink job？
         *         =》 取决于 调用了n个 executeAsync()
         *         =》 对应 application集群里，会有n个job
         *         =》 对应 Jobmanager当中，会有 n个 JobMaster
         */
    }

    //【2】source：集合中读数
    @Test
    public void test2() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 2. 创建集合
        List<Integer> data = Arrays.asList(1, 22, 3);
        // 3. 集合中创建数据流
        DataStreamSource<Integer> ds = env.fromCollection(data);
        // 4. 打印
        ds.print();
        // 5. 执行
        env.execute();
    }

    //【3】source：文件中读数
    @Test
    public void test3() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 2. 设置并行度
        env.setParallelism(1);
        // 3. 读取文件数据
        FileSource<String> fileSource = FileSource.forRecordStreamFormat(new TextLineInputFormat(), new Path("/Users/bytedance/Documents/sortware/workhouse/Flink-java/src/main/resources/input/words.txt")).build();
        // 4. 创建数据源流
        env.fromSource(fileSource, WatermarkStrategy.noWatermarks(), "file").print();
        env.execute();
    }


    // 【4】source：Socket中读数
    @Test
    public void test4() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 2. 读取文本流：hadoop102表示发送端主机名、7777表示端口号
        DataStreamSource<String> lineStream = env.socketTextStream("10.254.190.46", 7777);
        // 3. 转换、分组、求和，得到统计结果
        SingleOutputStreamOperator<Tuple2<String, Long>> sum = lineStream.flatMap((String line, Collector<Tuple2<String, Long>> out) -> {
                    String[] words = line.split(" ");
                    for (String word : words) {
                        out.collect(Tuple2.of(word, 1L));
                    }
                }).returns(Types.TUPLE(Types.STRING, Types.LONG))
                .keyBy(data -> data.f0)
                .sum(1);
        // 4. 打印
        sum.print();
        // 5. 执行
        env.execute();
    }


    //【5】source：Kafka中读数
    @Test
    public void test5() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 2. 创建KafkaSource
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers("hadoop102:9092")  //主机端口号
                .setTopics("topic_1") //消费主题
                .setGroupId("zijie") //消费组
                .setStartingOffsets(OffsetsInitializer.latest()) //开始消费偏移量
                .setValueOnlyDeserializer(new SimpleStringSchema()) //
                .build();
        // 3. 创建数据源流
        DataStreamSource<String> stream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "kafka-source");
        // 4. 打印
        stream.print("Kafka");
        // 5. 开启执行
        env.execute();
    }

    //【6】source：数据生成器
    @Test
    public void test6() throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 2. 设置并行度
        env.setParallelism(1);
        // 3. 创建数据生成器
        DataGeneratorSource<String> dataGeneratorSource = new DataGeneratorSource<>(
                        new GeneratorFunction<Long, String>() {
                            @Override
                            public String map(Long value) throws Exception {
                                return "Number:" + value;
                            }
                        },
                        Long.MAX_VALUE,
                        RateLimiterStrategy.perSecond(10),
                        Types.STRING
                );
        // 3. 创建数据源流，并打印
        env.fromSource(dataGeneratorSource, WatermarkStrategy.noWatermarks(), "datagenerator").print();
        // 4. 开启执行
        env.execute();
    }
}
