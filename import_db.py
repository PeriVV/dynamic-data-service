#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据库初始化脚本
用于导入init.sql文件中的数据到MySQL数据库
"""

import mysql.connector
import os
import sys

def connect_to_mysql():
    """连接到MySQL数据库"""
    try:
        connection = mysql.connector.connect(
            host='localhost',
            user='root',
            password='111111',
            port=3306,
            charset='utf8mb4'
        )
        return connection
    except mysql.connector.Error as err:
        print(f"连接MySQL失败: {err}")
        return None

def execute_sql_file(connection, sql_file_path):
    """执行SQL文件"""
    try:
        cursor = connection.cursor()
        
        # 读取SQL文件
        with open(sql_file_path, 'r', encoding='utf-8') as file:
            lines = file.readlines()
        
        # 处理SQL语句，合并多行语句
        current_statement = ""
        statements = []
        
        for line in lines:
            line = line.strip()
            # 跳过空行和注释
            if not line or line.startswith('#') or line.startswith('--'):
                continue
            
            current_statement += " " + line
            
            # 如果行以分号结尾，表示语句结束
            if line.endswith(';'):
                statements.append(current_statement.strip())
                current_statement = ""
        
        # 如果还有未完成的语句
        if current_statement.strip():
            statements.append(current_statement.strip())
        
        # 执行每个SQL语句
        for i, statement in enumerate(statements):
            if not statement:
                continue
                
            try:
                print(f"执行SQL语句 {i+1}: {statement[:80]}...")
                cursor.execute(statement)
                connection.commit()
                print(f"✓ 执行成功")
            except mysql.connector.Error as err:
                print(f"✗ 执行失败: {err}")
                print(f"  语句: {statement}")
                # 继续执行下一个语句
                continue
        
        cursor.close()
        print("\n数据库初始化完成！")
        
    except Exception as e:
        print(f"执行SQL文件失败: {e}")
        return False
    
    return True

def verify_data(connection):
    """验证数据是否导入成功"""
    try:
        cursor = connection.cursor()
        
        # 切换到目标数据库
        cursor.execute("USE dynamic_data_service")
        
        # 检查用户表数据
        cursor.execute("SELECT COUNT(*) FROM users")
        user_count = cursor.fetchone()[0]
        print(f"用户表记录数: {user_count}")
        
        # 检查产品表数据
        cursor.execute("SELECT COUNT(*) FROM products")
        product_count = cursor.fetchone()[0]
        print(f"产品表记录数: {product_count}")
        
        # 检查订单表数据
        cursor.execute("SELECT COUNT(*) FROM orders")
        order_count = cursor.fetchone()[0]
        print(f"订单表记录数: {order_count}")
        
        # 显示用户表的前几条记录
        cursor.execute("SELECT id, name, email FROM users LIMIT 3")
        users = cursor.fetchall()
        print("\n用户表示例数据:")
        for user in users:
            print(f"  ID: {user[0]}, 姓名: {user[1]}, 邮箱: {user[2]}")
        
        cursor.close()
        
    except mysql.connector.Error as err:
        print(f"验证数据失败: {err}")

def main():
    """主函数"""
    print("开始导入数据库...")
    
    # 检查SQL文件是否存在
    sql_file = 'init.sql'
    if not os.path.exists(sql_file):
        print(f"错误: 找不到SQL文件 {sql_file}")
        sys.exit(1)
    
    # 连接数据库
    connection = connect_to_mysql()
    if not connection:
        print("无法连接到数据库，请检查MySQL服务是否启动")
        sys.exit(1)
    
    try:
        # 执行SQL文件
        if execute_sql_file(connection, sql_file):
            print("\n验证导入结果...")
            verify_data(connection)
        else:
            print("数据导入失败")
            
    finally:
        # 关闭连接
        connection.close()
        print("\n数据库连接已关闭")

if __name__ == '__main__':
    main()