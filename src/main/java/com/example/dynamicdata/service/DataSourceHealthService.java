// src/main/java/com/example/dynamicdata/service/DataSourceHealthService.java
package com.example.dynamicdata.service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.springframework.stereotype.Service;

@Service
public class DataSourceHealthService {

    public Status check(DataSource ds, String validationQuery) {
        if (ds == null) {
            return new Status(false, "数据源未配置");
        }
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery(validationQuery)) {
                return new Status(true, "连接正常");
            }
        } catch (Exception e) {
            return new Status(false, "连接失败: " + e.getMessage());
        }
    }

    public static class Status {
        private boolean connected;
        private String message;

        public Status() {}
        public Status(boolean connected, String message) {
            this.connected = connected;
            this.message = message;
        }
        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
