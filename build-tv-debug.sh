#!/bin/bash
# 快速编译电视版 debug APK

echo "正在编译电视版 debug APK..."
./gradlew :app:assembleLeanbackArm64_v8aDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ 编译成功！"
    echo ""
    echo "APK 位置："
    echo "  ./app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk"
    echo ""
else
    echo ""
    echo "❌ 编译失败"
    echo ""
fi
