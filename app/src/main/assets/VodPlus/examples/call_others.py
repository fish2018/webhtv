#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Python 调用其他运行时示例

使用方法：在实验室 Python 包中运行此脚本
"""

import os
import subprocess
import sys

def call_binary(env_name, display_name, args):
    """调用其他二进制文件"""
    bin_path = os.environ.get(env_name)
    
    if not bin_path:
        print(f"错误: {display_name} 未安装或环境变量 {env_name} 不存在")
        return False
    
    print(f"{display_name} 路径: {bin_path}")
    
    try:
        result = subprocess.run(
            [bin_path] + args,
            capture_output=True,
            text=True,
            timeout=10
        )
        print(result.stdout)
        if result.stderr:
            print("stderr:", result.stderr)
        return True
    except subprocess.TimeoutExpired:
        print("执行超时")
        return False
    except Exception as e:
        print(f"执行失败: {e}")
        return False

def main():
    print("=" * 50)
    print("Python 调用其他运行时示例")
    print("=" * 50)
    print()
    
    # 调用 PHP
    print("=== 调用 PHP ===")
    if call_binary("PHP_BIN", "PHP", ["-v"]):
        print()
        print("执行 PHP 代码:")
        call_binary("PHP_BIN", "PHP", ["-r", 'echo "Hello from PHP!\\n";'])
    print()
    
    # 调用 Node.js
    print("=== 调用 Node.js ===")
    if call_binary("NODEJS_BIN", "Node.js", ["-v"]):
        print()
        print("执行 JavaScript 代码:")
        call_binary("NODEJS_BIN", "Node.js", ["-e", 'console.log("Hello from Node.js!")'])
    print()
    
    # 显示所有可用的环境变量
    print("=== 可用的二进制环境变量 ===")
    bin_vars = [key for key in os.environ.keys() if key.endswith("_BIN")]
    if bin_vars:
        for var in sorted(bin_vars):
            print(f"  {var} = {os.environ[var]}")
    else:
        print("  (无)")

if __name__ == "__main__":
    main()
