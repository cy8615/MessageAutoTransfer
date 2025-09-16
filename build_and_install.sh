#!/bin/bash

# 消息转发器编译和安装脚本

echo "🚀 开始编译消息转发器..."

# 检查Android设备连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 没有检测到已连接的Android设备"
    echo "请确保："
    echo "1. 设备已通过USB连接到电脑"
    echo "2. 已开启开发者选项和USB调试"
    echo "3. 已授权USB调试"
    exit 1
fi

echo "✅ 检测到Android设备已连接"

# 清理之前的构建
echo "🧹 清理之前的构建文件..."
./gradlew clean

# 编译Debug版本
echo "🔨 编译Debug版本..."
if ./gradlew assembleDebug; then
    echo "✅ 编译成功"
else
    echo "❌ 编译失败"
    exit 1
fi

# 安装到设备
echo "📱 安装到Android设备..."
if adb install -r app/build/outputs/apk/debug/app-debug.apk; then
    echo "✅ 安装成功"
else
    echo "❌ 安装失败"
    exit 1
fi

echo ""
echo "🎉 消息转发器已成功安装到您的设备！"
echo ""
echo "📋 接下来的步骤："
echo "1. 在设备上打开'消息转发器'应用"
echo "2. 配置您的邮箱信息（建议使用QQ邮箱）"
echo "3. 点击设置按钮，开启通知访问权限"
echo "4. 返回应用，开启转发服务开关"
echo "5. 发送测试消息验证转发功能"
echo ""
echo "⚠️ 注意："
echo "- QQ邮箱需要使用授权码，不是登录密码"
echo "- 确保网络连接正常"
echo "- 首次使用需要手动开启通知访问权限"
