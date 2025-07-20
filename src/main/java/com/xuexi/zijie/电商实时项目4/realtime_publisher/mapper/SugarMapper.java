package com.xuexi.zijie.电商实时项目4.realtime_publisher.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface SugarMapper {

    @Select("SELECT SUM(order_amount) order_amount FROM dws_trade_sku_order_window WHERE cur_date=#{date}")
    BigDecimal selectGmv(@Param("date") String date);

    @Select("SELECT trademark_name,SUM(order_amount) order_amount FROM dws_trade_sku_order_window WHERE cur_date=#{date} GROUP BY trademark_name ORDER BY order_amount DESC LIMIT #{limit}")
    List<Map<String, Object>> selectGmvByTrademark(@Param("date") String date,
                                                   @Param("limit") int limit);

    @Select("select province_name,\n" +
            "\t sum(order_count) counts\n" +
            "from dws_trade_province_order_window\n" +
            "where cur_date=#{date}\n" +
            "group by province_name")
    List<Map<String, Object>> selectOrderCountByProvince(@Param("date") String date);

}
