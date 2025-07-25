package com.xuexi.zijie.电商实时项目4.realtime_common.util;


import com.xuexi.zijie.电商实时项目4.realtime_common.constant.Constant;

/**
 * @author yhm
 * @create 2023-12-23 9:34
 */
public class SQLUtil {

    public static String getKafkaSourceSQL(String topicName,String groupId){
        return "WITH (\n" +
                "  'connector' = 'kafka',\n" +
                "  'topic' = '" + topicName + "',\n" +
                "  'properties.bootstrap.servers' = '" + Constant.KAFKA_BROKERS +"',\n" +
                "  'properties.group.id' = '" + groupId + "',\n" +
                "  'scan.startup.mode' = 'earliest-offset',\n" +
                "  'format' = 'json'\n" +
                ")";
    }

    public static String getKafkaTopicDb(String groupId){
        return "CREATE TABLE topic_db (\n" +
                "  `database` STRING,\n" +
                "  `table` STRING,\n" +
                "  `type` STRING,\n" +
                "  `data` map<STRING,STRING>,\n" +
                "  `old` map<STRING,STRING>,\n" +
                "  `ts` bigint,\n" +
                "  proc_time as PROCTIME(), \n" +
                "  row_time as TO_TIMESTAMP_LTZ(ts,3), \n" +
                "  WATERMARK FOR row_time AS row_time - INTERVAL '5' SECOND\n" +
                ") " + getKafkaSourceSQL(Constant.TOPIC_DB, groupId);
    }


    public static String getKafkaSinkSQL(String topicName){
        return "WITH (\n" +
                "  'connector' = 'kafka',\n" +
                "  'topic' = '" + topicName + "',\n" +
                "  'properties.bootstrap.servers' = '" + Constant.KAFKA_BROKERS +"',\n" +
                "  'format' = 'json'\n" +
                ")";
    }

    /**
     * 获取upsert kafka的连接   创建表格的语句最后一定要声明主键
     * @param topicName
     * @return
     */
    public static String getUpsertKafkaSQL(String topicName){
        return "WITH (\n" +
                "  'connector' = 'upsert-kafka',\n" +
                "  'topic' = '" + topicName + "',\n" +
                "  'properties.bootstrap.servers' = '" + Constant.KAFKA_BROKERS +"',\n" +
                "  'key.format' = 'json',\n" +
                "  'value.format' = 'json'\n" +
                ")";
    }

    public static String getDorisSinkSQL(String tableName){
        return "WITH (\n" +
                "      'connector' = 'doris',\n" +
                "      'fenodes' = '" + Constant.FENODES + "',\n" +
                "      'table.identifier' = '" + Constant.DORIS_DATABASE + "." +  tableName + "',\n" +
                "      'username' = '"  + Constant.DORIS_USERNAME+ "',\n" +
                "      'password' = '" + Constant.DORIS_PASSWORD + "',\n" +
                "      'sink.label-prefix' = 'doris_label" + System.currentTimeMillis() + "'\n" +
                ")";
    }
}
