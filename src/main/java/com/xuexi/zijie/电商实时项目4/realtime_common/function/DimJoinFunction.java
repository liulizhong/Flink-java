package com.xuexi.zijie.电商实时项目4.realtime_common.function;

import com.alibaba.fastjson.JSONObject;

/**
 * @author yhm
 * @create 2024-01-03 14:15
 */
public interface DimJoinFunction<T> {
    public  String getId( T input);
    public  String getTableName();
    public  void join(T input, JSONObject dim);
}
