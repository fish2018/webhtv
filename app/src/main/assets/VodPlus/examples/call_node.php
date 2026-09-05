<?php
/**
 * PHP 调用 Node.js 示例
 * 
 * 使用方法：在实验室 PHP 包中运行此脚本
 */

// 从环境变量获取 Node.js 二进制路径
$nodeBin = getenv('NODEJS_BIN');

if (!$nodeBin) {
    echo "错误: Node.js 未安装或环境变量 NODEJS_BIN 不存在\n";
    echo "请先在实验室中安装 Node.js 包\n";
    exit(1);
}

echo "Node.js 路径: $nodeBin\n\n";

// 示例 1: 获取 Node.js 版本
echo "=== Node.js 版本 ===\n";
$version = shell_exec("$nodeBin -v 2>&1");
echo $version . "\n";

// 示例 2: 执行 JavaScript 代码
echo "=== 执行 JavaScript 代码 ===\n";
$jsCode = 'console.log("Hello from Node.js!"); console.log("当前时间:", new Date().toLocaleString());';
$output = shell_exec("$nodeBin -e '$jsCode' 2>&1");
echo $output . "\n";

// 示例 3: 获取系统信息
echo "=== 系统信息 (通过 Node.js) ===\n";
$sysInfo = 'console.log("平台:", process.platform); console.log("架构:", process.arch); console.log("Node版本:", process.version);';
$output = shell_exec("$nodeBin -e '$sysInfo' 2>&1");
echo $output;
