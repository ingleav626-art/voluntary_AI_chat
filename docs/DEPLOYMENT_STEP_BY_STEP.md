# 云端部署实战教程（一步一步）

## 前置准备

### 你需要准备的东西

1. **一台云服务器**（阿里云、腾讯云、华为云等）
   - 推荐：Ubuntu 20.04/22.04 或 CentOS 7/8
   - 配置：2核4G内存（最低配置）
   - 端口：需要开放80、443端口

2. **一个域名**（可选，但推荐）
   - 用于HTTPS加密
   - 例如：`your-domain.com`

3. **本地开发环境**
   - Java 17
   - Maven 3.6+
   - Git

---

## 第一步：构建后端JAR文件（本地操作）

### 1.1 在本地电脑上构建

打开PowerShell，进入项目目录：

```powershell
cd D:\voluntary_AI_chat
```

构建后端JAR：

```powershell
mvn clean package -pl server -am '-Dcheckstyle.skip=true' '-DskipTests'
```

等待构建完成（约1-2分钟），看到 `BUILD SUCCESS` 表示成功。

### 1.2 验证构建结果

查看生成的JAR文件：

```powershell
Get-ChildItem server\target\*.jar | Select-Object Name, @{Name="Size(MB)";Expression={[math]::Round($_.Length/1MB,2)}}
```

应该看到两个文件：
- `voluntary-ai-chat-server-1.0-SNAPSHOT.jar`（普通JAR，较小）
- `voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar`（可执行JAR，约50MB）

**我们只需要 `voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar` 这个文件。**

---

## 第二步：准备服务器环境（服务器操作）

### 2.1 连接到服务器

使用SSH连接到你的云服务器：

```bash
ssh root@your-server-ip
```

或使用Windows Terminal：

```powershell
ssh root@your-server-ip
```

### 2.2 安装Java 17

**Ubuntu/Debian**：

```bash
sudo apt update
sudo apt install openjdk-17-jdk -y
```

**CentOS/RHEL**：

```bash
sudo yum install java-17-openjdk-devel -y
```

验证Java版本：

```bash
java -version
```

应该显示：`openjdk version "17.x.x"`

### 2.3 安装MySQL

**Ubuntu/Debian**：

```bash
sudo apt install mysql-server -y
sudo systemctl start mysql
sudo systemctl enable mysql
```

**CentOS/RHEL**：

```bash
sudo yum install mysql-server -y
sudo systemctl start mysqld
sudo systemctl enable mysqld
```

### 2.4 配置MySQL

登录MySQL：

```bash
sudo mysql
```

创建数据库和用户：

```sql
-- 创建数据库
CREATE DATABASE voluntary_ai_chat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户（替换密码为强密码）
CREATE USER 'voluntary_user'@'localhost' IDENTIFIED BY '9sK$7pR2&zQ5!dF8';
GRANT ALL PRIVILEGES ON voluntary_ai_chat.* TO 'voluntary_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

**⚠️ 重要：请将 `YourStrongPassword123!` 替换为你的强密码（至少16位，包含大小写字母、数字、特殊字符）**

### 2.5 安装Redis

**Ubuntu/Debian**：

```bash
sudo apt install redis-server -y
sudo systemctl start redis
sudo systemctl enable redis
```

**CentOS/RHEL**：

```bash
sudo yum install redis -y
sudo systemctl start redis
sudo systemctl enable redis
```

### 2.6 配置Redis密码

编辑Redis配置文件：

```bash
sudo vi /etc/redis/redis.conf
```

找到 `# requirepass foobared` 这一行，取消注释并修改密码：

```
requirepass YourRedisPassword123!
```

重启Redis：

```bash
sudo systemctl restart redis
```

测试Redis连接：

```bash
redis-cli -a YourRedisPassword123 ping
```

应该返回：`PONG`

---

## 第三步：上传JAR文件到服务器

### 3.1 创建应用目录

在服务器上创建应用目录：

```bash
sudo mkdir -p /opt/voluntary-ai-chat
sudo mkdir -p /opt/voluntary-ai-chat/logs
sudo mkdir -p /opt/voluntary-ai-chat/uploads/chat/images
sudo chown -R $USER:$USER /opt/voluntary-ai-chat
```

### 3.2 上传JAR文件

在本地电脑上，使用SCP上传JAR文件：

```powershell
scp server\target\voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar root@your-server-ip:/opt/voluntary-ai-chat/
```

或使用FTP/SFTP工具（如FileZilla）上传。

---

## 第四步：配置环境变量

### 4.1 创建配置文件

在服务器上创建环境变量配置文件：

```bash
vi /opt/voluntary-ai-chat/config.env
```

写入以下内容（**请替换为你的实际配置**）：

```bash
# 数据库配置
export DB_HOST=localhost
export DB_USER=voluntary_user
export DB_PASSWORD=YourStrongPassword123!

# Redis配置
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=YourRedisPassword123!

# JWT密钥（必须修改为强密钥）
# 使用以下命令生成强密钥：openssl rand -base64 32
export JWT_SECRET=CHANGE_THIS_TO_RANDOM_256BIT_SECRET

# CORS配置（允许客户端域名）
# 如果有域名，填写：https://your-domain.com
# 如果没有域名，填写：http://your-server-ip:8080
export CORS_ALLOWED_ORIGINS=http://your-server-ip:8080

# 图片上传URL
# 如果有域名，填写：https://your-domain.com/files
# 如果没有域名，填写：http://your-server-ip:8080/files
export UPLOAD_BASE_URL=http://your-server-ip:8080/files
```

**⚠️ 重要：请替换以下内容**
- `YourStrongPassword123!` → 你的MySQL密码
- `YourRedisPassword123!` → 你的Redis密码
- `CHANGE_THIS_TO_RANDOM_256BIT_SECRET` → JWT强密钥（使用 `openssl rand -base64 32` 生成）
- `your-server-ip` → 你的服务器IP地址

---

## 第五步：启动后端服务

### 5.1 创建启动脚本

创建启动脚本：

```bash
vi /opt/voluntary-ai-chat/start.sh
```

写入以下内容：

```bash
#!/bin/bash

# 加载配置文件
source /opt/voluntary-ai-chat/config.env

# 启动应用（云端模式）
nohup java -jar voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar \
  --spring.profiles.active=cloud \
  --server.address=127.0.0.1 \
  > logs/server.log 2>&1 &

echo "Server started on port 8080 (localhost only)"
echo "PID: $!"
echo "Log file: /opt/voluntary-ai-chat/logs/server.log"
```

### 5.2 创建停止脚本

创建停止脚本：

```bash
vi /opt/voluntary-ai-chat/stop.sh
```

写入以下内容：

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

### 5.3 启动服务

启动后端服务：

```bash
cd /opt/voluntary-ai-chat
chmod +x start.sh stop.sh
./start.sh
```

### 5.4 查看日志

查看启动日志：

```bash
tail -f /opt/voluntary-ai-chat/logs/server.log
```

看到以下日志表示启动成功：

```
Started VoluntaryAiChatApplication in x.xxx seconds
非热点模式：服务器启动完成，不启动广播服务
```

按 `Ctrl+C` 退出日志查看。

---

## 第六步：配置防火墙（重要安全步骤）

### 6.1 配置防火墙（Ubuntu/Debian）

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow ssh
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw deny 8080/tcp
sudo ufw deny 3306/tcp
sudo ufw deny 6379/tcp
sudo ufw enable
```

查看防火墙状态：

```bash
sudo ufw status verbose
```

### 6.2 配置防火墙（CentOS/RHEL）

```bash
sudo firewall-cmd --permanent --default-zone=public
sudo firewall-cmd --permanent --add-service=ssh
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --permanent --remove-port=8080/tcp
sudo firewall-cmd --permanent --remove-port=3306/tcp
sudo firewall-cmd --permanent --remove-port=6379/tcp
sudo firewall-cmd --reload
```

查看防火墙状态：

```bash
sudo firewall-cmd --list-all
```

---

## 第七步：配置Nginx反向代理（可选，但推荐）

### 7.1 安装Nginx

**Ubuntu/Debian**：

```bash
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
```

**CentOS/RHEL**：

```bash
sudo yum install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
```

### 7.2 配置Nginx

创建Nginx配置文件：

```bash
sudo vi /etc/nginx/sites-available/voluntary-ai-chat.conf
```

写入以下内容（**如果有域名**）：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 反向代理到后端（仅localhost:8080）
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket代理
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

    # 图片文件代理
    location /files {
        alias /opt/voluntary-ai-chat/uploads/chat/images;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

**如果没有域名**，使用IP地址：

```nginx
server {
    listen 80;
    server_name your-server-ip;

    # 反向代理到后端
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket代理
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

    # 图片文件代理
    location /files {
        alias /opt/voluntary-ai-chat/uploads/chat/images;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

启用配置：

```bash
sudo ln -s /etc/nginx/sites-available/voluntary-ai-chat.conf /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### 7.3 测试Nginx

测试Nginx是否正常：

```bash
curl http://localhost/api/auth/test
```

应该返回后端的响应。

---

## 第八步：客户端配置和测试

### 8.1 配置客户端

在本地电脑上，打开PowerShell，设置云端模式：

```powershell
$env:SERVER_MODE="cloud"
$env:CLOUD_SERVER_URL="http://your-server-ip/api"
```

**如果有域名**：

```powershell
$env:SERVER_MODE="cloud"
$env:CLOUD_SERVER_URL="https://your-domain.com/api"
```

### 8.2 启动客户端

启动JavaFX客户端：

```powershell
mvn javafx:run -pl client
```

### 8.3 测试连接

在客户端中：
1. 注册新用户
2. 登录
3. 测试AI聊天
4. 测试WebSocket连接

如果一切正常，表示部署成功！

---

## 第九步：创建Systemd服务（推荐）

### 9.1 创建Systemd服务文件

创建服务文件：

```bash
sudo vi /etc/systemd/system/voluntary-ai-chat.service
```

写入以下内容：

```ini
[Unit]
Description=Voluntary AI Chat Server
After=network.target mysql.service redis.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/voluntary-ai-chat
EnvironmentFile=/opt/voluntary-ai-chat/config.env
ExecStart=/usr/bin/java -jar voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar --spring.profiles.active=cloud --server.address=127.0.0.1
ExecStop=/bin/kill -15 $MAINPID
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### 9.2 启用服务

启用并启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable voluntary-ai-chat
sudo systemctl start voluntary-ai-chat
```

查看服务状态：

```bash
sudo systemctl status voluntary-ai-chat
```

查看服务日志：

```bash
sudo journalctl -u voluntary-ai-chat -f
```

---

## 第十步：验证部署成功

### 10.1 检查服务状态

检查后端服务是否运行：

```bash
ps aux | grep voluntary-ai-chat-server
```

检查端口是否监听：

```bash
sudo netstat -tulpn | grep 8080
```

应该看到：`127.0.0.1:8080`（仅监听localhost）

### 10.2 检查防火墙

检查防火墙是否正确配置：

```bash
sudo ufw status verbose
```

应该看到：
- 80/tcp ALLOW
- 443/tcp ALLOW
- 8080/tcp DENY
- 3306/tcp DENY
- 6379/tcp DENY

### 10.3 测试API

测试API是否正常：

```bash
curl http://localhost/api/auth/test
```

应该返回后端的响应。

---

## 常见问题排查

### 问题1：无法启动服务

**可能原因**：
- MySQL连接失败
- Redis连接失败
- JWT密钥配置错误

**解决方案**：
查看日志：

```bash
tail -f /opt/voluntary-ai-chat/logs/server.log
```

### 问题2：客户端无法连接

**可能原因**：
- 防火墙配置错误
- Nginx配置错误
- CORS配置错误

**解决方案**：
检查防火墙：

```bash
sudo ufw status
```

检查Nginx：

```bash
sudo nginx -t
```

### 问题3：WebSocket连接失败

**可能原因**：
- Nginx WebSocket代理配置错误

**解决方案**：
检查Nginx配置中的 `/ws` location是否正确。

---

## 部署成功清单

- [ ] Java 17已安装
- [ ] MySQL已安装并创建数据库
- [ ] Redis已安装并设置密码
- [ ] JAR文件已上传到服务器
- [ ] 环境变量已配置
- [ ] 后端服务已启动
- [ ] 防火墙已配置（仅开放80、443）
- [ ] Nginx已配置反向代理
- [ ] Systemd服务已创建
- [ ] 客户端已配置云端模式
- [ ] 客户端已成功连接

---

## 下一步

部署成功后，你可以：
1. 配置HTTPS（使用Let's Encrypt）
2. 配置域名解析
3. 配置日志监控
4. 配置数据库备份
5. 配置自动重启

---

## 总结

云端部署的核心步骤：

1. **本地构建**：`mvn clean package -pl server -am`
2. **服务器准备**：安装Java、MySQL、Redis
3. **上传JAR**：SCP上传到 `/opt/voluntary-ai-chat`
4. **配置环境变量**：数据库、Redis、JWT密钥
5. **启动服务**：`./start.sh`
6. **配置防火墙**：仅开放80、443
7. **配置Nginx**：反向代理到localhost:8080
8. **客户端配置**：设置云端模式

部署完成后，用户通过JavaFX客户端连接云端服务器，实现真人聊天和多人协作。