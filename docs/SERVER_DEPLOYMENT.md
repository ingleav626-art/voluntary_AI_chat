# 后端部署指南

## 一、项目结构说明

```
voluntary_AI_chat/
├── client/          # JavaFX客户端（桌面应用）
├── server/          # Spring Boot后端（服务端）
├── common/          # 公共模块（客户端和服务端共用）
├── docs/            # 文档
└── data/            # 本地数据库文件（H2）
```

**后端模块（server）**：
- Spring Boot 3 + MyBatis-Plus + MySQL + Redis
- WebSocket实时通信
- JWT认证
- 支持三种启动模式：local、hotspot、cloud

---

## 二、云服务部署安全架构

**⚠️ 安全铁律**：

```
┌─────────────────────────────────────────────────────────┐
│                     云服务器                              │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Nginx（唯一入口）                                 │  │
│  │  - 监听80、443端口（对外开放）                      │  │
│  │  - HTTPS加密                                       │  │
│  │  - 反向代理到localhost:8080                        │  │
│  └──────────────────────────────────────────────────┘  │
│                          │                               │
│                          ▼                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Spring Boot后端                                   │  │
│  │  - 仅监听127.0.0.1:8080（不对外开放）              │  │
│  │  - WebSocket服务                                   │  │
│  └──────────────────────────────────────────────────┘  │
│                          │                               │
│          ┌───────────────┴───────────────┐              │
│          ▼                               ▼              │
│  ┌────────────────┐            ┌────────────────┐     │
│  │  MySQL数据库    │            │  Redis缓存      │     │
│  │  - 仅监听       │            │  - 仅监听       │     │
│  │  localhost:3306│            │  localhost:6379│     │
│  │  （不对外开放） │            │  （不对外开放） │     │
│  └────────────────┘            └────────────────┘     │
│                                                          │
│  防火墙规则：                                             │
│  - 仅开放80、443端口（Nginx）                            │
│  - 关闭8080、3306、6379端口                              │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                     客户端                                │
│  - 通过HTTPS连接Nginx                                    │
│  - WebSocket通过wss://your-domain.com/ws                │
│  - 无法直接访问后端、数据库、Redis                        │
└─────────────────────────────────────────────────────────┘
```

**关键安全原则**：
1. **Nginx是唯一的入口**：所有请求必须通过Nginx
2. **后端仅监听localhost**：`--server.address=127.0.0.1`
3. **数据库仅监听localhost**：MySQL bind-address=127.0.0.1
4. **Redis仅监听localhost**：Redis bind 127.0.0.1
5. **防火墙仅开放80、443**：关闭所有内部端口

---

## 三、构建后端模块

### 1. 构建可执行JAR

```bash
# 在项目根目录执行
mvn clean package -pl server -am -DskipTests

# 构建结果：
# server/target/voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar（可执行JAR，包含所有依赖）
# server/target/voluntary-ai-chat-server-1.0-SNAPSHOT.jar（普通JAR，供其他模块依赖）
```

### 2. 构建说明

- `-pl server`：仅构建server模块
- `-am`：同时构建依赖的模块（common）
- `-DskipTests`：跳过测试（生产环境建议运行测试）

---

## 四、配置文件修改

### 1. 云端配置文件

使用 `application-cloud.yml` 作为生产环境配置：

```yaml
# 关键配置项（通过环境变量覆盖）
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:3306/voluntary_ai_chat
    username: ${DB_USER:voluntary_user}
    password: ${DB_PASSWORD:YourStrongPassword123!}

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:YourRedisPassword123!}

jwt:
  secret: ${JWT_SECRET:CHANGE_THIS_TO_RANDOM_256BIT_SECRET_IN_PRODUCTION}

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,https://your-domain.com}

upload:
  image:
    base-url: https://your-domain.com/files
```

### 2. 环境变量配置

在服务器上设置环境变量（推荐方式）：

```bash
# 数据库配置
export DB_HOST=your-db-server.com
export DB_USER=voluntary_user
export DB_PASSWORD=YourStrongPassword123!

# Redis配置
export REDIS_HOST=your-redis-server.com
export REDIS_PORT=6379
export REDIS_PASSWORD=YourRedisPassword123!

# JWT密钥（必须修改为强密钥）
export JWT_SECRET=$(openssl rand -base64 32)

# CORS配置（允许客户端域名）
export CORS_ALLOWED_ORIGINS=https://your-domain.com

# 图片上传URL
export UPLOAD_BASE_URL=https://your-domain.com/files
```

---

## 五、数据库准备

### 1. 创建MySQL数据库

```sql
-- 创建数据库
CREATE DATABASE voluntary_ai_chat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户（生产环境）
CREATE USER 'voluntary_user'@'%' IDENTIFIED BY 'YourStrongPassword123!';
GRANT ALL PRIVILEGES ON voluntary_ai_chat.* TO 'voluntary_user'@'%';
FLUSH PRIVILEGES;
```

### 2. 初始化数据库表结构

数据库表结构在 `server/src/main/resources/db/schema.sql`，启动时自动执行（如果配置了 `spring.sql.init.mode=always`）。

**生产环境建议**：
- 手动执行schema.sql
- 或使用数据库迁移工具（如Flyway）

```bash
# 手动初始化数据库
mysql -u voluntary_user -p voluntary_ai_chat < server/src/main/resources/db/schema.sql
```

---

## 六、Redis准备

### 1. 安装Redis

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install redis-server
sudo systemctl enable redis-server
sudo systemctl start redis-server

# CentOS/RHEL
sudo yum install redis
sudo systemctl enable redis
sudo systemctl start redis
```

### 2. 配置Redis密码

```bash
# 编辑Redis配置文件
sudo vi /etc/redis/redis.conf

# 设置密码
requirepass YourRedisPassword123!

# 重启Redis
sudo systemctl restart redis-server
```

### 3. 测试Redis连接

```bash
redis-cli -h localhost -p 6379 -a YourRedisPassword123 ping
# 应返回：PONG
```

---

## 七、部署步骤（Linux服务器）

### 1. 准备服务器环境

```bash
# 安装Java 17
sudo apt update
sudo apt install openjdk-17-jdk

# 验证Java版本
java -version
# 应显示：openjdk version "17.x.x"

# 创建应用目录
sudo mkdir -p /opt/voluntary-ai-chat
sudo chown $USER:$USER /opt/voluntary-ai-chat
```

### 2. 上传JAR文件

```bash
# 从本地上传到服务器
scp server/target/voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar user@your-server:/opt/voluntary-ai-chat/

# 或使用FTP/SFTP工具上传
```

### 3. 创建启动脚本

创建 `/opt/voluntary-ai-chat/start.sh`：

```bash
#!/bin/bash

# 设置环境变量
export DB_HOST=localhost
export DB_USER=voluntary_user
export DB_PASSWORD=YourStrongPassword123!
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=YourRedisPassword123!
export JWT_SECRET=$(openssl rand -base64 32)
export CORS_ALLOWED_ORIGINS=https://your-domain.com
### 4. 启动应用

**⚠️ 重要安全提示**：
- **不要直接暴露8080端口**，仅允许内网访问（localhost或内网IP）
- **必须通过Nginx反向代理**，只暴露80/443端口
- 防火墙应仅开放80、443端口，关闭8080端口

```bash
# 启动后端（云端模式，仅监听localhost）
java -jar voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar \
  --spring.profiles.active=cloud \
  --server.port=8080 \
  --server.address=127.0.0.1 \
  > logs/server.log 2>&1 &

echo "Server started on port 8080 (localhost only)"
echo "PID: $!"
``` 创建停止脚本

创建 `/opt/voluntary-ai-chat/stop.sh`：

```bash
#!/bin/bash

# 查找并停止进程
PID=$(ps aux | grep 'voluntary-ai-chat-server' | grep -v grep | awk '{print $2}')

if [ -n "$PID" ]; then
  kill $PID
  echo "Server stopped (PID: $PID)"
else
  echo "Server not running"
fi
```

### 5. 防火墙配置（重要）

**⚠️ 安全铁律**：
- **仅开放80、443端口**（Nginx端口）
- **关闭8080端口**（后端服务器端口）
- **关闭3306端口**（MySQL端口）
- **关闭6379端口**（Redis端口）

```bash
# Ubuntu/Debian防火墙配置
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow ssh
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw deny 8080/tcp
sudo ufw deny 3306/tcp
sudo ufw deny 6379/tcp
sudo ufw enable

# 查看防火墙状态
sudo ufw status verbose
```

**CentOS/RHEL防火墙配置**：

```bash
sudo firewall-cmd --permanent --default-zone=public
sudo firewall-cmd --permanent --add-service=ssh
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --permanent --remove-port=8080/tcp
sudo firewall-cmd --permanent --remove-port=3306/tcp
sudo firewall-cmd --permanent --remove-port=6379/tcp
sudo firewall-cmd --reload

# 查看防火墙状态
sudo firewall-cmd --list-all
```

### 6. 创建Systemd服务（推荐）

创建 `/etc/systemd/system/voluntary-ai-chat.service`：

```ini
[Unit]
Description=Voluntary AI Chat Server
After=network.target mysql.service redis.service

[Service]
Type=simple
User=voluntary
WorkingDirectory=/opt/voluntary-ai-chat
Environment="DB_HOST=localhost"
Environment="DB_USER=voluntary_user"
Environment="DB_PASSWORD=YourStrongPassword123!"
Environment="REDIS_HOST=localhost"
Environment="REDIS_PORT=6379"
Environment="REDIS_PASSWORD=YourRedisPassword123!"
Environment="JWT_SECRET=CHANGE_THIS_TO_RANDOM_256BIT_SECRET"
Environment="CORS_ALLOWED_ORIGINS=https://your-domain.com"
# 仅监听localhost，不暴露端口
ExecStart=/usr/bin/java -jar voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar \
  --spring.profiles.active=cloud \
  --server.address=127.0.0.1
ExecStop=/bin/kill -15 $MAINPID
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
# 启用服务
sudo systemctl enable voluntary-ai-chat

# 启动服务
sudo systemctl start voluntary-ai-chat

# 查看状态
sudo systemctl status voluntary-ai-chat

# 查看日志
sudo journalctl -u voluntary-ai-chat -f
```

---

## 八、Nginx反向代理配置（唯一入口）

**⚠️ 安全铁律**：
- **Nginx是唯一的入口**，所有请求必须通过Nginx
- **后端服务器仅监听localhost**，不暴露任何端口
- **数据库和Redis仅监听localhost**，不暴露任何端口

### 1. 安装Nginx

```bash
sudo apt install nginx
sudo systemctl enable nginx
sudo systemctl start nginx
```

### 2. 配置Nginx

创建 `/etc/nginx/sites-available/voluntary-ai-chat.conf`：

```nginx
# HTTP重定向到HTTPS
server {
    listen 80;
    server_name your-domain.com;

    # 重定向到HTTPS
    return 301 https://$server_name$request_uri;
}

# HTTPS服务器（唯一入口）
server {
    listen 443 ssl http2;
    server_name your-domain.com;

    # SSL证书配置（使用Let's Encrypt）
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    # SSL安全配置
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # 反向代理到后端（仅localhost:8080）
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket代理（仅localhost:8080）
    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400;
    }

    # 图片文件代理（本地文件）
    location /files {
        alias /opt/voluntary-ai-chat/uploads/chat/images;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

启用配置：

```bash
# 创建软链接
sudo ln -s /etc/nginx/sites-available/voluntary-ai-chat.conf /etc/nginx/sites-enabled/

# 测试配置
sudo nginx -t

# 重载Nginx
sudo systemctl reload nginx
```

### 3. 获取SSL证书（Let's Encrypt）

```bash
# 安装Certbot
sudo apt install certbot python3-certbot-nginx

# 获取证书
sudo certbot --nginx -d your-domain.com

# 自动续期
sudo certbot renew --dry-run
```

---

## 九、客户端连接配置

### 1. 修改客户端配置

在客户端配置文件 `application-client.properties` 中设置云端服务器地址：

```properties
# 云端服务器地址
cloud.server.url=https://your-domain.com/api
```

或通过环境变量启动客户端：

```bash
# Windows PowerShell
$env:SERVER_MODE="cloud"
$env:CLOUD_SERVER_URL="https://your-domain.com/api"
mvn javafx:run -pl client

# Linux/Mac
export SERVER_MODE=cloud
export CLOUD_SERVER_URL=https://your-domain.com/api
mvn javafx:run -pl client
```

### 2. 三种启动模式

客户端支持三种启动模式：

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| LOCAL | 本地模式（内嵌后端） | 开发测试、隐私数据 |
| HOTSPOT | 热点模式（局域网） | 局域网测试、多人协作 |
| CLOUD | 云端模式（公网服务器） | 真人聊天、多人协作 |

---

## 十、安全建议

### 1. 数据库安全

- 使用强密码（至少16位，包含大小写字母、数字、特殊字符）
- 限制数据库用户权限（仅授予必要权限）
- 定期备份数据库
- 启用MySQL SSL连接

### 2. Redis安全

- 设置强密码
- 禁用危险命令（FLUSHALL、FLUSHDB、CONFIG）
- 绑定内网IP（bind 127.0.0.1 或内网IP）
- 启用Redis SSL连接（可选）

### 3. JWT安全

- 使用强密钥（至少256位）
- 定期更换密钥
- 设置合理的过期时间（access token: 2小时，refresh token: 7天）

### 4. 网络安全

- 启用HTTPS（强制）
- 配置防火墙（仅开放必要端口）
- 使用Nginx反向代理（隐藏后端端口）
- 启用CORS白名单（仅允许客户端域名）

### 5. 应用安全

- 定期更新依赖版本
- 启用日志审计
- 监控异常请求
- 定期检查安全漏洞

---

## 十一、监控与日志

### 1. 应用日志

日志文件位置：`/opt/voluntary-ai-chat/logs/server.log`

查看日志：

```bash
# 实时查看日志
tail -f /opt/voluntary-ai-chat/logs/server.log

# 查看最近100行
tail -n 100 /opt/voluntary-ai-chat/logs/server.log

# 搜索错误日志
grep "ERROR" /opt/voluntary-ai-chat/logs/server.log
```

### 2. Systemd日志

```bash
# 实时查看Systemd日志
sudo journalctl -u voluntary-ai-chat -f

# 查看最近100行
sudo journalctl -u voluntary-ai-chat -n 100
```

### 3. 性能监控

推荐工具：
- Prometheus + Grafana（监控指标）
- ELK Stack（日志分析）
- Spring Boot Actuator（应用监控）

---

## 十二、故障排查

### 1. 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 无法启动 | 数据库连接失败 | 检查数据库配置、网络连接 |
| 无法启动 | Redis连接失败 | 检查Redis配置、密码 |
| JWT验证失败 | 密钥不匹配 | 检查JWT_SECRET环境变量 |
| WebSocket连接失败 | Nginx配置错误 | 检查WebSocket代理配置 |
| 图片上传失败 | 目录权限错误 | 检查uploads目录权限 |

### 2. 检查端口占用

```bash
# 检查8080端口
sudo netstat -tulpn | grep 8080

# 检查3306端口（MySQL）
sudo netstat -tulpn | grep 3306

# 检查6379端口（Redis）
sudo netstat -tulpn | grep 6379
```

### 3. 检查防火墙

```bash
# Ubuntu/Debian
sudo ufw status

# CentOS/RHEL
sudo firewall-cmd --list-all
```

---

## 十三、备份与恢复

### 1. 数据库备份

```bash
# 备份数据库
mysqldump -u voluntary_user -p voluntary_ai_chat > backup_$(date +%Y%m%d).sql

# 恢复数据库
mysql -u voluntary_user -p voluntary_ai_chat < backup_20260623.sql
```

### 2. 应用备份

```bash
# 备份应用文件
tar -czf app_backup_$(date +%Y%m%d).tar.gz /opt/voluntary-ai-chat

# 恢复应用文件
tar -xzf app_backup_20260623.tar.gz -C /
```

---

## 十四、总结

部署后端到服务器的主要步骤：

1. **构建后端模块**：`mvn clean package -pl server -am -DskipTests`
2. **准备数据库**：创建MySQL数据库和用户
3. **准备Redis**：安装并配置Redis密码
4. **上传JAR文件**：上传到服务器 `/opt/voluntary-ai-chat`
5. **配置环境变量**：设置数据库、Redis、JWT等配置
6. **启动应用**：使用Systemd服务管理
7. **配置Nginx**：反向代理和SSL证书
8. **客户端连接**：设置云端服务器地址

部署完成后，客户端可以通过云端模式连接服务器，实现真人聊天和多人协作。