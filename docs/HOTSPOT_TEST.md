# 热点测试环境搭建指南

> 使用手机热点搭建局域网测试环境，无需云端服务器

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

### 2.3 配置服务器

1. **修改客户端热点配置**：
   编辑 `client/src/main/resources/application-hotspot.properties`
   ```properties
   client.base-url=http://192.168.43.100:8080/api  # 替换为服务器电脑的实际IP
   ```

2. **启动服务器**：
   ```bash
   mvn spring-boot:run -pl server -Dspring-boot.run.profiles=hotspot
   ```

### 2.4 启动客户端

**方式一：使用环境变量（推荐）**
```bash
# Windows PowerShell
$env:CLIENT_CONFIG="application-hotspot.properties"
mvn javafx:run -pl client

# Linux/Mac
export CLIENT_CONFIG=application-hotspot.properties
mvn javafx:run -pl client
```

**方式二：修改默认配置文件**
```bash
# 临时替换配置文件
cp client/src/main/resources/application-client.properties client/src/main/resources/application-client.properties.bak
cp client/src/main/resources/application-hotspot.properties client/src/main/resources/application-client.properties
mvn javafx:run -pl client
# 测试完成后恢复
mv client/src/main/resources/application-client.properties.bak client/src/main/resources/application-client.properties
```

---

## 三、测试连接

### 3.1 验证服务器启动

服务器启动成功后，访问：
```
http://192.168.43.100:8080/api/auth/sms/send
```

如果返回响应，说明服务器已正确监听局域网地址。

### 3.2 验证客户端连接

客户端启动后，尝试登录或注册，观察日志：
```
INFO  c.v.c.client.service.AuthService - 登录成功
```

如果出现连接失败，检查：
1. 服务器是否启动
2. IP地址是否正确
3. 防火墙是否阻止8080端口

---

## 四、常见问题

### 4.1 防火墙阻止连接

**Windows防火墙**：
```bash
# 添加防火墙规则允许8080端口
netsh advfirewall firewall add rule name="Spring Boot 8080" dir=in action=allow protocol=tcp localport=8080
```

**Linux防火墙**：
```bash
# Ubuntu/Debian
sudo ufw allow 8080

# CentOS/RHEL
sudo firewall-cmd --add-port=8080/tcp --permanent
sudo firewall-cmd --reload
```

### 4.2 IP地址变化

手机热点重启后，IP地址可能变化。需要：
1. 重新查看服务器电脑的IP地址
2. 更新客户端配置文件
3. 重启客户端

### 4.3 连接超时

检查：
1. 网络连接是否稳定
2. 服务器是否正常运行
3. 客户端配置的超时时间是否合理

---

## 五、生产环境部署

生产环境建议：
1. 使用云服务器（阿里云、腾讯云等）
2. 配置固定IP地址
3. 使用域名代替IP地址
4. 配置HTTPS加密传输

---

## 六、配置文件说明

### 服务器配置
- `application.yml`：本地开发配置（localhost）
- `application-hotspot.yml`：热点测试配置（0.0.0.0）

### 客户端配置
- `application-client.properties`：本地开发配置（localhost）
- `application-hotspot.properties`：热点测试配置（局域网IP）

### 环境变量
- `CLIENT_CONFIG`：指定客户端配置文件名称

---

## 七、启动命令汇总

```bash
# 本地开发（服务器和客户端在同一台电脑）
mvn spring-boot:run -pl server
mvn javafx:run -pl client

# 热点测试（局域网环境）
# 服务器
mvn spring-boot:run -pl server -Dspring-boot.run.profiles=hotspot

# 客户端（使用环境变量）
$env:CLIENT_CONFIG="application-hotspot.properties"  # Windows PowerShell
mvn javafx:run -pl client
```