# 热点测试环境搭建指南

> 使用手机热点搭建局域网测试环境，无需云端服务器。支持服务器自动发现功能。

---

## 一、环境准备

### 1.1 硬件要求
- **服务器电脑**：运行 Spring Boot 后端服务
- **客户端设备**：运行 JavaFX 客户端（可以是另一台电脑或同一台电脑）
- **手机热点**：提供局域网连接

### 1.2 软件要求
- JDK 17+
- MySQL 8.0+
- Maven 3.6+

### 1.3 自动发现功能
服务器启动后会通过UDP广播自己的IP地址，客户端启动时会自动监听广播或扫描局域网，无需手动配置IP地址。

---

## 二、搭建步骤

### 2.1 开启手机热点

1. 打开手机热点功能
2. 设置热点名称和密码
3. 记录热点网络信息

### 2.2 连接设备到热点

**服务器电脑连接热点**：
1. 连接手机热点
2. 查看局域网IP地址：
   ```bash
   # Windows
   ipconfig

   # Linux/Mac
   ifconfig
   ```
3. 找到热点网络对应的IP地址（通常是 `192.168.43.x` 或 `192.168.x.x`）

**客户端设备连接热点**：
- 连接同一个手机热点
- 确保与服务器电脑在同一局域网

### 2.3 启动服务器

**方式一：自动发现（推荐）**
```bash
mvn spring-boot:run -pl server -Dspring-boot.run.profiles=hotspot
```
服务器启动后会自动广播IP地址，客户端无需手动配置。

**方式二：手动配置**
如果自动发现失败，可以手动配置客户端：
1. 编辑 `client/src/main/resources/application-hotspot.properties`
   ```properties
   client.base-url=http://192.168.43.100:8080/api  # 替换为服务器电脑的实际IP
   ```
2. 启动服务器：
   ```bash
   mvn spring-boot:run -pl server -Dspring-boot.run.profiles=hotspot
   ```

### 2.4 启动客户端

**方式一：自动发现（推荐）**
```bash
mvn javafx:run -pl client
```
客户端启动时会自动监听UDP广播或扫描局域网，发现服务器地址。

**方式二：使用环境变量**
```bash
# Windows PowerShell
$env:CLIENT_CONFIG="application-hotspot.properties"
mvn javafx:run -pl client

# Linux/Mac
export CLIENT_CONFIG=application-hotspot.properties
mvn javafx:run -pl client
```

**方式三：修改默认配置文件**
```bash
# 临时替换配置文件
cp client/src/main/resources/application-client.properties client/src/main/resources/application-client.properties.bak
cp client/src/main/resources/application-hotspot.properties client/src/main/resources/application-client.properties
mvn javafx:run -pl client
# 测试完成后恢复
mv client/src/main/resources/application-client.properties.bak client/src/main/resources/application-client.properties
```

---

## 三、自动发现原理

### 3.1 服务器广播
- 服务器启动后，通过UDP端口9876广播自己的IP地址和端口
- 广播消息格式：`VOLUNTARY_CHAT_SERVER:192.168.43.100:8080`
- 每5秒广播一次，确保客户端能及时接收

### 3.2 客户端发现
客户端启动时采用两阶段发现策略：

**阶段一：监听UDP广播（5秒超时）**
- 监听UDP端口9876
- 解析广播消息获取服务器地址
- 如果收到广播，立即使用该地址

**阶段二：主动扫描（广播超时后）**
- 扫描常见局域网IP段：
  - 192.168.43.x（Android热点）
  - 192.168.0.x（路由器）
  - 192.168.1.x（路由器）
  - 10.0.0.x（部分路由器）
  - 172.16.0.x（企业网络）
- 并发扫描，每个IP尝试连接8080端口（500ms超时）
- 返回第一个发现的服务器地址

---

## 四、测试连接

### 4.1 验证服务器启动

服务器启动成功后，访问：
```
http://192.168.43.100:8080/api/auth/sms/send
```

如果返回响应，说明服务器已正确监听局域网地址。

### 4.2 验证客户端连接

客户端启动后，观察日志：
```
INFO  o.e.client.App - 尝试自动发现服务器...
INFO  o.e.client.util.ServerDiscovery - 发现服务器: http://192.168.43.100:8080/api
INFO  o.e.client.App - 自动发现服务器成功: http://192.168.43.100:8080/api
INFO  o.e.client.App - 客户端配置加载成功: baseUrl=http://192.168.43.100:8080/api
```

如果出现连接失败，检查：
1. 服务器是否启动
2. UDP端口9876是否被防火墙阻止
3. 防火墙是否阻止8080端口

---

## 五、常见问题

### 5.1 防火墙阻止连接

**Windows防火墙**：
```bash
# 添加防火墙规则允许8080端口
netsh advfirewall firewall add rule name="Spring Boot 8080" dir=in action=allow protocol=tcp localport=8080

# 添加防火墙规则允许UDP广播端口9876
netsh advfirewall firewall add rule name="UDP Broadcast 9876" dir=in action=allow protocol=udp localport=9876
```

**Linux防火墙**：
```bash
# Ubuntu/Debian
sudo ufw allow 8080
sudo ufw allow 9876/udp

# CentOS/RHEL
sudo firewall-cmd --add-port=8080/tcp --permanent
sudo firewall-cmd --add-port=9876/udp --permanent
sudo firewall-cmd --reload
```

### 5.2 IP地址变化

手机热点重启后，IP地址可能变化。自动发现功能会自动适应IP变化，无需手动修改配置。

### 5.3 连接超时

检查：
1. 网络连接是否稳定
2. 服务器是否正常运行
3. 客户端配置的超时时间是否合理

### 5.4 自动发现失败

如果自动发现失败，可能原因：
1. UDP广播被防火墙阻止
2. 服务器未启动广播服务
3. 客户端和服务器不在同一局域网

解决方法：
- 使用手动配置方式
- 检查防火墙设置
- 确认服务器已启动

---

## 六、生产环境部署

生产环境建议：
1. 使用云服务器（阿里云、腾讯云等）
2. 配置固定IP地址
3. 使用域名代替IP地址
4. 配置HTTPS加密传输

---

## 七、配置文件说明

### 服务器配置
- `application.yml`：本地开发配置（localhost）
- `application-hotspot.yml`：热点测试配置（0.0.0.0）

### 客户端配置
- `application-client.properties`：本地开发配置（localhost）
- `application-hotspot.properties`：热点测试配置（局域网IP）

### 环境变量
- `CLIENT_CONFIG`：指定客户端配置文件名称

---

## 八、启动命令汇总

```bash
# 本地开发（服务器和客户端在同一台电脑）
mvn spring-boot:run -pl server
mvn javafx:run -pl client

# 热点测试（局域网环境，自动发现）
# 服务器
mvn spring-boot:run -pl server -Dspring-boot.run.profiles=hotspot

# 客户端（自动发现服务器）
mvn javafx:run -pl client

# 客户端（手动配置）
$env:CLIENT_CONFIG="application-hotspot.properties"  # Windows PowerShell
mvn javafx:run -pl client
```