cmake_minimum_required(VERSION 3.22.1)
project(cnode)

# 只编桥接层。libnode.so 由 App 在运行时按设备架构下载到私有目录，
# 用 dlopen 打开、dlsym 取 node::Start，所以这里不做链接期依赖。
add_library(cnode SHARED node_bridge.cpp)

find_library(log-lib log)
target_link_libraries(cnode ${log-lib} dl)
