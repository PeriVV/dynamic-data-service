package com.example.dynamicdata.datasource;

public enum DataSourceType {
    MYSQL, POSTGRESQL, DM8;

    public static DataSourceType from(String s) {
        if (s == null) return null;
        switch (s.trim().toUpperCase()) {
            case "MYSQL": return MYSQL;
            case "POSTGRESQL": return POSTGRESQL;
            case "DM8": return DM8;
            default: return null;
        }
    }
}
