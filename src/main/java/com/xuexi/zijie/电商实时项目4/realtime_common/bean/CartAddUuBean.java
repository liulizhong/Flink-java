package com.xuexi.zijie.电商实时项目4.realtime_common.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author yhm
 * @create 2023-12-29 10:05
 */
@Data
@AllArgsConstructor
public class CartAddUuBean {
    // 窗口起始时间
    String stt;
    // 窗口闭合时间
    String edt;
    // 当天日期
    String curDate;
    // 加购独立用户数
    Long cartAddUuCt;
}
