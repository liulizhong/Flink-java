package com.xuexi.zijie.base;

import com.xuexi.zijie.bean.WaterSensor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.annotation.InputGroup;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.functions.AggregateFunction;
import org.apache.flink.table.functions.ScalarFunction;
import org.apache.flink.table.functions.TableAggregateFunction;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;
import org.junit.Test;

import static org.apache.flink.table.api.Expressions.$;
import static org.apache.flink.table.api.Expressions.call;

public class _16_第十章_TableAPI_Function {

    /**
     * 1、TableAPI 案例
     */
    @Test
    public void test1() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // TODO 1.创建表环境
        // 1.1 写法一：默认就是流处理模式
        // StreamTableEnvironment tableEnv1 = (StreamTableEnvironment) TableEnvironment.create(EnvironmentSettings.newInstance().inStreamingMode().build());
        // 1.2 写法二：对于流处理场景，其实默认配置就完全够用了。所以我们也可以用另一种更加简单的方式来创建表环境：
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
        // TODO 2.创建表
        tableEnv.executeSql(
                "CREATE TABLE source ( \n" +
                        "    id INT, \n" +
                        "    ts BIGINT, \n" +
                        "    vc INT\n" +
                        ") WITH ( \n" +
                        "    'connector' = 'datagen', \n" +
                        "    'rows-per-second'='1', \n" +
                        "    'fields.id.kind'='random', \n" +
                        "    'fields.id.min'='1', \n" +
                        "    'fields.id.max'='10', \n" +
                        "    'fields.ts.kind'='sequence', \n" +
                        "    'fields.ts.start'='1', \n" +
                        "    'fields.ts.end'='1000000', \n" +
                        "    'fields.vc.kind'='random', \n" +
                        "    'fields.vc.min'='1', \n" +
                        "    'fields.vc.max'='100'\n" +
                        ");\n");
        tableEnv.executeSql(
                "CREATE TABLE sink (\n" +
                        "    id INT, \n" +
                        "    sumVC INT \n" +
                        ") WITH (\n" +
                        "'connector' = 'print'\n" +
                        ");\n");
        // TODO 3.执行查询
        // 3.1 使用sql进行查询
        Table table = tableEnv.sqlQuery("select id,sum(vc) as sumVC from source where id>5 group by id ;");
        // 把table对象，注册成表名
        tableEnv.createTemporaryView("tmp", table);
        tableEnv.sqlQuery("select * from tmp where id > 7");

        // 3.2 用table api来查询
        Table source = tableEnv.from("source");
        Table result = source.where($("id").isGreater(5))
                .groupBy($("id"))
                .aggregate($("vc").sum().as("sumVC"))
                .select($("id"), $("sumVC"));

        // TODO 4.输出表
        // 4.1 sql用法
        tableEnv.executeSql("insert into sink select * from tmp");
        // 4.2 tableapi用法
        result.executeInsert("sink");
    }


    /**
     * 2、表和流的转换
     */
    @Test
    public void test2() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        DataStreamSource<WaterSensor> sensorDS = env.fromElements(
                new WaterSensor("s1", 1L, 1),
                new WaterSensor("s1", 2L, 2),
                new WaterSensor("s2", 2L, 2),
                new WaterSensor("s3", 3L, 3),
                new WaterSensor("s3", 4L, 4)
        );
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // TODO 1. 流转表
        Table sensorTable = tableEnv.fromDataStream(sensorDS);
        tableEnv.createTemporaryView("sensor", sensorTable);

        Table filterTable = tableEnv.sqlQuery("select id,ts,vc from sensor where ts>2");
        Table sumTable = tableEnv.sqlQuery("select id,sum(vc) from sensor group by id");

        // TODO 2. 表转流
        // 2.1 追加流
        tableEnv.toDataStream(filterTable, WaterSensor.class).print("filter");
        // 2.2 changelog流(结果需要更新)
        tableEnv.toChangelogStream(sumTable).print("sum");

        // 只要代码中调用了 DataStreamAPI，就需要 execute，否则不需要
        env.execute();
    }


    /**
     * 3、自定义函数
     */
    @Test
    public void test3() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        DataStreamSource<WaterSensor> sensorDS = env.fromElements(
                new WaterSensor("s1 s1", 1L, 1),
                new WaterSensor("s1 s1", 2L, 2),
                new WaterSensor("s2 s3", 2L, 2),
                new WaterSensor("s3 s4", 3L, 3),
                new WaterSensor("s3 s1", 4L, 4)
        );
        // 创建表环境
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
        // 流转表，并注册表名
        Table sensorTable = tableEnv.fromDataStream(sensorDS);
        tableEnv.createTemporaryView("sensor", sensorTable);

        // TODO 1. 注册函数：标量函数（Scalar Functions）
        tableEnv.createTemporaryFunction("HashFunction", HashFunction.class);
        // （1）用函数：sql用法
        tableEnv.sqlQuery("select HashFunction(id) from sensor")
                .execute()  // 调用了 sql的execute，就不需要 env.execute()
                .print();
        // （2）用函数：table api用法
        sensorTable
                .select(call("HashFunction",$("id")))
                .execute()
                .print();

        // TODO 2. 注册函数：表函数（Table Functions）
        tableEnv.createTemporaryFunction("SplitFunction", SplitFunction.class);
        // 1 用函数：交叉联结
        tableEnv.sqlQuery("select id,word,length from sensor,lateral table(SplitFunction(id))").execute().print();
        // 2 用函数：带 on true 条件的 左联结
        tableEnv.sqlQuery("select id,word,length from sensor left join lateral table(SplitFunction(id)) on true").execute().print();
        // 3 用函数：重命名侧向表中的字段
        tableEnv.sqlQuery("select id,newWord,newLength from sensor left join lateral table(SplitFunction(id)) as T(newWord,newLength) on true").execute().print();

        // TODO 3.注册函数：聚合函数（Aggregate Functions）
        tableEnv.createTemporaryFunction("WeightedAvg", WeightedAvg.class);
        // 用函数
        tableEnv.sqlQuery("select id,WeightedAvg(ts,vc)  from sensor group by id").execute().print();

        // TODO 4. 注册函数：表聚合函数（Table Aggregate Functions）
        tableEnv.createTemporaryFunction("Top2", Top2.class);
        // 用函数
        sensorTable.flatAggregate(call("Top2", $("vc")).as("value", "rank"))
                .select( $("value"), $("rank"))
                .execute().print();
    }

    // TODO 1.继承 Scalar Functions
    public static class HashFunction extends ScalarFunction{
        // 接受任意类型的输入，返回 INT型输出
        public int eval(@DataTypeHint(inputGroup = InputGroup.ANY) Object o) {
            return o.hashCode();
        }
    }

    // TODO 2.继承 TableFunction<返回的类型> ，类型标注：Row包含两个字段：word和length
    @FunctionHint(output = @DataTypeHint("ROW<word STRING,length INT>"))
    public static class SplitFunction extends TableFunction<Row> {
        // 返回是 void，用 collect方法输出
        public void eval(String str) {
            for (String word : str.split(" ")) {
                collect(Row.of(word, word.length()));
            }
        }
    }

    // TODO 3.继承 AggregateFunction< 返回类型，累加器类型<加权总和，权重总和> >
    public static class WeightedAvg extends AggregateFunction<Double, Tuple2<Long, Integer>> {
        @Override
        public Double getValue(Tuple2<Long, Integer> integerIntegerTuple2) {
            return integerIntegerTuple2.f0 * 1D / integerIntegerTuple2.f1;
        }

        @Override
        public Tuple2<Long, Integer> createAccumulator() {
            return Tuple2.of(0L, 0);
        }

        /**
         * 累加计算的方法，每来一行数据都会调用一次
         * @param acc 累加器类型
         * @param score 第一个参数：分数
         * @param weight 第二个参数：权重
         */
        public void accumulate(Tuple2<Long, Integer> acc,Long score,Integer weight){
            acc.f0 += score * weight;  // 加权总和 =  分数1 * 权重1 + 分数2 * 权重2 +....
            acc.f1 += weight;         // 权重和 = 权重1 + 权重2 +....
        }
    }

    // TODO 4.继承 TableAggregateFunction< 返回类型，累加器类型<加权总和，权重总和> >
    // 返回类型 (数值，排名) =》 (12,1) (9,2)
    // 累加器类型 (第一大的数，第二大的数) ===》 （12,9）
    public static class Top2 extends TableAggregateFunction<Tuple2<Integer, Integer>, Tuple2<Integer, Integer>> {
        @Override
        public Tuple2<Integer, Integer> createAccumulator() {
            return Tuple2.of(0, 0);
        }
        /**
         * 每来一个数据调用一次，比较大小，更新 最大的前两个数到 acc中
         */
        public void accumulate(Tuple2<Integer, Integer> acc, Integer num) {
            if (num > acc.f0) {
                // 新来的变第一，原来的第一变第二
                acc.f1 = acc.f0;
                acc.f0 = num;
            } else if (num > acc.f1) {
                // 新来的变第二，原来的第二不要了
                acc.f1 = num;
            }
        }

        /**
         * 输出结果： （数值，排名）两条最大的
         */
        public void emitValue(Tuple2<Integer, Integer> acc, Collector<Tuple2<Integer, Integer>> out) {
            if (acc.f0 != 0) {
                out.collect(Tuple2.of(acc.f0, 1));
            }
            if (acc.f1 != 0) {
                out.collect(Tuple2.of(acc.f1, 2));
            }
        }
    }
}
