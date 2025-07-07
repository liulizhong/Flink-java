package com.xuexi.zijie.base;

import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import com.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.junit.Test;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;

import java.util.Properties;

public class _17_附一章_FlinkCDC {
    /**
     * 1、FlinkCDC_DataStream
     */
    @Test
    public void test1() throws Exception {




        // TODO 1. 准备流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // TODO 2. 开启检查点   Flink-CDC将读取binlog的位置信息以状态的方式保存在CK,如果想要做到断点续传,
        // 需要从Checkpoint或者Savepoint启动程序
        // 2.1 开启Checkpoint,每隔3秒钟做一次CK  ,并指定CK的一致性语义
        env.enableCheckpointing(3000L, CheckpointingMode.EXACTLY_ONCE);         // 方式1:直接在开启Checkpoint时，指定精准一次语意
//        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE); // 方式2:单独设置指定精准一次语意
        // 2.2 设置超时时间为 1 分钟
        env.getCheckpointConfig().setCheckpointTimeout(60 * 1000L);
        // 2.3 设置两次重启的最小时间间隔
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(3000L);
        // 2.4 设置任务关闭的时候保留最后一次 CK 数据
        env.getCheckpointConfig().enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        // 2.5 指定从 CK 自动重启策略
        env.setRestartStrategy(RestartStrategies.failureRateRestart(3, Time.days(1L), Time.minutes(1L)));
        // 2.6 设置状态后端
        env.setStateBackend(new HashMapStateBackend());
        env.getCheckpointConfig().setCheckpointStorage("hdfs://hadoop102:8020/flinkCDC/ck");
        // 2.7 设置访问HDFS的用户名
        System.setProperty("HADOOP_USER_NAME", "atguigu");
        // 2.8 设置同时运行中的checkpoint的最大数量
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);

        //3. TODO 3. 创建 Flink-MySQL-CDC 的 Source
        MySqlSource<String> mySqlSource = MySqlSource.<String>builder()
                .hostname("hadoop103")
                .port(3306)
                .username("root")
                .password("000000")
                .databaseList("test") // set captured database
                .tableList("test.t1") //在写表时，需要带上库名。如果什么都不写，则表示监控所有的表
                .deserializer(new JsonDebeziumDeserializationSchema()) // converts SourceRecord to JSON String
                .startupOptions(StartupOptions.initial())
                .build();

        // TODO 4.使用CDC Source从MySQL读取数据
        DataStreamSource<String> mysqlDS = env.fromSource(mySqlSource, WatermarkStrategy.noWatermarks(), "mysql-source");

        //5.打印
        mysqlDS.print();
        //6.启动
        env.execute();
    }

    /**
     * 2、FlinkCDC_SQL
     */
    @Test
    public void test2() {
        //1.获取Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        //2.使用FlinkCDC SQL方式建表
        tableEnv.executeSql("" +
                        "create table t1(\n" +
                        "    id string primary key NOT ENFORCED,\n" +
                        "    name string" +
                        ") WITH (\n" +
                        " 'connector' = 'mysql-cdc',\n" +
                        " 'hostname' = 'hadoop103',\n" +
                        " 'port' = '3306',\n" +
                        " 'username' = 'root',\n" +
                        " 'password' = '000000',\n" +
                        " 'database-name' = 'test',\n" +
                        " 'table-name' = 't1'\n" +
                        ")");

        //3.查询并打印
        Table table = tableEnv.sqlQuery("select * from t1");
        table.execute().print();
    }
}
