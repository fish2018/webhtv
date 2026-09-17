/**
 * Node.js 调用 PHP 示例
 * 
 * 使用方法：在实验室 Node.js 包中运行此脚本
 */

const { spawnSync } = require("child_process");

// 从环境变量获取 PHP 二进制路径
const phpBin = process.env.PHP_BIN;

if (!phpBin) {
    console.log("错误: PHP 未安装或环境变量 PHP_BIN 不存在");
    console.log("请先在实验室中安装 PHP 包");
    process.exit(1);
}

console.log("PHP 路径:", phpBin);
console.log("");

// 示例 1: 获取 PHP 版本
console.log("=== PHP 版本 ===");
const versionResult = spawnSync(phpBin, ["-v"]);
if (versionResult.error) {
    console.log("执行失败:", versionResult.error.message);
} else {
    console.log(versionResult.stdout.toString());
}

// 示例 2: 执行 PHP 代码
console.log("=== 执行 PHP 代码 ===");
const codeResult = spawnSync(phpBin, ["-r", 'echo "Hello from PHP!\\n"; echo "当前时间: " . date("Y-m-d H:i:s") . "\\n";']);
if (codeResult.error) {
    console.log("执行失败:", codeResult.error.message);
} else {
    console.log(codeResult.stdout.toString());
}

// 示例 3: 查看 PHP 模块
console.log("=== PHP 已加载模块 ===");
const modulesResult = spawnSync(phpBin, ["-m"]);
if (modulesResult.error) {
    console.log("执行失败:", modulesResult.error.message);
} else {
    const modules = modulesResult.stdout.toString().split("\n").filter(m => m.trim());
    console.log("共加载", modules.length, "个模块");
    console.log(modules.slice(0, 10).join(", "), "...");
}
