package com.xuexi.zijie.bean;

/**
 * 用户登陆类
 */
public class LoginEvent {
    public String userId;
    public String ipAddress;
    public String eventType;
    public Long timestamp;
    public int amount;


    public LoginEvent(String userId, String ipAddress, String eventType, Long timestamp, int amount) {
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.amount = amount;
    }
    public LoginEvent(String userId, String ipAddress, String eventType, Long timestamp) {
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.eventType = eventType;
        this.timestamp = timestamp;
    }

    public LoginEvent() {
    }

    @Override
    public String toString() {
        return "LoginEvent{" +
                "userId='" + userId + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", eventType='" + eventType + '\'' +
                ", timestamp=" + timestamp +
                ", amount=" + amount +
                '}';
    }
}