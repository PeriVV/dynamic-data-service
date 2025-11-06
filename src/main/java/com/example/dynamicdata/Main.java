package com.example.dynamicdata;

import java.sql.SQLException;

public class Main {
    public  static void main(String[] args) throws ClassNotFoundException, SQLException {
        Class.forName("dm.jdbc.driver.DmDriver");
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                "jdbc:dm://127.0.0.1:5236", "SYSDBA", "Abcd12345678")) {
            System.out.println("OK: " + !c.isClosed());
        }

    }
}
