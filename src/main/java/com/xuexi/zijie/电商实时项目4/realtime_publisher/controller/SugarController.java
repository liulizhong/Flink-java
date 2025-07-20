package com.xuexi.zijie.电商实时项目4.realtime_publisher.controller;

import com.xuexi.zijie.电商实时项目4.realtime_publisher.service.SugarService;
import com.xuexi.zijie.电商实时项目4.realtime_publisher.util.DateFormatUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

//@Controller
@RestController
public class SugarController {

    @Autowired
    private SugarService sugarService;


    @RequestMapping("test")
    //@ResponseBody
    public String test01() {
        System.out.println("aaaaaaaaa");
        return "success";
    }

    @RequestMapping("test02")
    public String test02(@RequestParam("nn") String name,
                         @RequestParam(value = "age", defaultValue = "18") int age) {
        System.out.println(name + ":" + age);
        return "success  " +name + ":" + age;
    }

    @RequestMapping("gmv")
    public String getGmv(@RequestParam(value = "date", defaultValue = "0") String date) {

        if ("0".equals(date)) {
            date = getToday();
        }

        BigDecimal gmv = sugarService.getGmv(date);

        if (gmv == null) {
            gmv = new BigDecimal("0.0");
        }

        return "{\n" +
                "  \"status\": 0,\n" +
                "  \"msg\": \"\",\n" +
                "  \"data\": " + gmv + "\n" +
                "}";
    }

    @RequestMapping("trademark")
    public String getGmvByTrademark(@RequestParam(value = "date", defaultValue = "0") String date,
                                    @RequestParam(value = "limit", defaultValue = "5") int limit) {

        if ("0".equals(date)) {
            date = getToday();
        }

        Map<String, BigDecimal> gmvByTrademark = sugarService.getGmvByTrademark(date, limit);
        //["1","2","3"]  ==> "1","2","3"
        Set<String> tmName = gmvByTrademark.keySet();
        Collection<BigDecimal> gmvs = gmvByTrademark.values();

        return "{\n" +
                "  \"status\": 0,\n" +
                "  \"msg\": \"\",\n" +
                "  \"data\": {\n" +
                "    \"categories\": [\"" + StringUtils.join(tmName, "\",\"") + "\"],\n" +
                "    \"series\": [\n" +
                "      {\n" +
                "        \"name\": \"GMV\",\n" +
                "        \"data\": ["+StringUtils.join(gmvs,",")+"]\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";
    }

    @RequestMapping("province")
    public String getOrderCountByProvince(@RequestParam(value = "date", defaultValue = "0") String date) {

        if ("0".equals(date)) {
            date = getToday();
        }

        Map<String, Long> orderCountByProvince = sugarService.getOrderCountByProvince(date);

        //["1","2","3"]  ==> "1","2","3"
        Set<String> provinceNames = orderCountByProvince.keySet();
//        Collection<Long> orderCounts = orderCountByProvince.values();
        ArrayList<String> strings = new ArrayList<>();
        for (String provinceName : provinceNames) {
            String stringBuilder = "{\n" +
                    "        \"name\": \"" +
                    provinceName +
                    "\",\n" +
                    "        \"value\": " +
                    orderCountByProvince.get(provinceName) +
                    "}";
            strings.add(stringBuilder);
        }

        return "{\n" +
                "  \"status\": 0,\n" +
                "  \"msg\": \"\",\n" +
                "  \"data\": {\n" +
                "    \"mapData\": \n" +
                StringUtils.join(strings) +
                "    ,\n" +
                "    \"valueName\": \"订单数\"\n" +
                "  }\n" +
                "}";
    }
    private String getToday() {
        return DateFormatUtil.toDate(System.currentTimeMillis());
    }

    public static void main(String[] args) {

        ArrayList<String> list = new ArrayList<>();
        list.add("aaa");
        list.add("bbb");
        list.add("cc");
        list.add("vvv");

        System.out.println(list);

        System.out.println(StringUtils.join(list, "-"));

    }

}
