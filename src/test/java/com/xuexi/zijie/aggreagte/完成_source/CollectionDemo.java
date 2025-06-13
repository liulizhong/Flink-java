package com.xuexi.zijie.aggreagte.完成_source;

import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * TODO
 *
 * @author cjp
 * @version 1.0
 */
public class CollectionDemo {
    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // TODO 从集合读取数据
        DataStreamSource<Integer> source = env
                .fromElements(1,2,33); // 从元素读
//                .fromCollection(Arrays.asList(1, 22, 3));  // 从集合读


        source.print();

        env.execute();

    }
}
