# 云端部署卸载教程

## 卸载概述

如果你需要卸载云端部署的Voluntary AI Chat后端，可以按照以下步骤进行。卸载过程会删除所有相关文件、服务和配置，但不会删除MySQL和Redis（除非你明确要求）。

---

## 第一步：停止并删除服务

### 1.1 停止服务

停止正在运行的后端服务：

```bash
sudo systemctl stop voluntary-ai-chat
```

或使用停止脚本：

```bash
cd /opt/voluntary-ai-chat
./stop.sh
```

### 1.2 删除Systemd服务

删除Systemd服务文件：

```bash
sudo systemctl disable voluntary-ai-chat
sudo rm /etc/systemd/system/voluntary-ai-chat.service
sudo systemctl daemon-reload
```

验证服务已删除：

```bash
sudo systemctl status voluntary-ai-chat
```

应该显示：`Unit voluntary-ai-chat.service could not be found.`

---

## 第二步：删除应用文件

### 2.1 删除应用目录

删除应用目录和所有文件：

```bash
sudo rm -rf /opt/voluntary-ai-chat
```

验证目录已删除：

```bash
ls /opt/voluntary-ai-chat
```

应该显示：`No such file or directory`

### 2.2 删除日志文件（如果单独存储）

如果日志文件存储在其他位置，删除它们：

```bash
sudo rm -rf /var/log/voluntary-ai-chat
```

---

## 第三步：删除Nginx配置（可选）

### 3.1 删除Nginx配置文件

删除Nginx配置文件：

```bash
sudo rm /etc/nginx/sites-enabled/voluntary-ai-chat.conf
sudo rm /etc/nginx/sites-available/voluntary-ai-chat.conf
sudo systemctl reload nginx
```

验证配置已删除：

```bash
sudo nginx -t
```

### 3.2 卸载Nginx（可选）

如果你不再需要Nginx，可以卸载：

**Ubuntu/Debian**：

```bash
sudo systemctl stop nginx
sudo systemctl disable nginx
sudo apt remove nginx -y
sudo apt autoremove -y
```

**CentOS/RHEL**：

```bash
sudo systemctl stop nginx
sudo systemctl disable nginx
sudo yum remove nginx -y
```

---

## 第四步：删除数据库（可选）

### 4.1 删除数据库

**⚠️ 警告：删除数据库会永久丢失所有数据，请谨慎操作！**

登录MySQL：

```bash
sudo mysql
```

删除数据库和用户：

```sql
-- 删除数据库
DROP DATABASE IF EXISTS voluntary_ai_chat;

-- 删除用户
DROP USER IF EXISTS 'voluntary_user'@'localhost';

-- 刷新权限
FLUSH PRIVILEGES;
EXIT;
```

验证数据库已删除：

```bash
sudo mysql -e "SHOW DATABASES;"
```

应该看不到 `voluntary_ai_chat` 数据库。

### 4.2 卸载MySQL（可选）

如果你不再需要MySQL，可以卸载：

**Ubuntu/Debian**：

```bash
sudo systemctl stop mysql
sudo systemctl disable mysql
sudo apt remove mysql-server -y
sudo apt autoremove -y
sudo rm -rf /var/lib/mysql
sudo rm -rf /etc/mysql
```

**CentOS/RHEL**：

```bash
sudo systemctl stop mysqld
sudo systemctl disable mysqld
sudo yum remove mysql-server -y
sudo rm -rf /var/lib/mysql
sudo rm -rf /etc/my.cnf
```

---

## 第五步：删除Redis密码（可选）

### 5.1 删除Redis密码

编辑Redis配置文件：

```bash
sudo vi /etc/redis/redis.conf
```

找到 `requirepass YourRedisPassword123!` 这一行，注释掉或删除：

```
# requirepass YourRedisPassword123!
```

重启Redis：

```bash
sudo systemctl restart redis
```

测试Redis连接（无需密码）：

```bash
redis-cli ping
```

应该返回：`PONG`

### 5.2 卸载Redis（可选）

如果你不再需要Redis，可以卸载：

**Ubuntu/Debian**：

```bash
sudo systemctl stop redis
sudo systemctl disable redis
sudo apt remove redis-server -y
sudo apt autoremove -y
sudo rm -rf /var/lib/redis
sudo rm -rf /etc/redis
```

**CentOS/RHEL**：

```bash
sudo systemctl stop redis
sudo systemctl disable redis
sudo yum remove redis -y
sudo rm -rf /var/lib/redis
sudo rm -rf /etc/redis.conf
```

---

## 第六步：卸载Java（可选）

### 6.1 卸载Java 17

如果你不再需要Java，可以卸载：

**Ubuntu/Debian**：

```bash
sudo apt remove openjdk-17-jdk -y
sudo apt autoremove -y
```

**CentOS/RHEL**：

```bash
sudo yum remove java-17-openjdk-devel -y
```

验证Java已卸载：

```bash
java -version
```

应该显示：`command not found`

---

## 第七步：清理防火墙规则（可选）

### 7.1 删除防火墙规则

如果你不再需要防火墙规则，可以删除：

**Ubuntu/Debian**：

```bash
sudo ufw delete allow 80/tcp
sudo ufw delete allow 443/tcp
sudo ufw delete deny 8080/tcp
sudo ufw delete deny 3306/tcp
sudo ufw delete deny 6379/tcp
```

查看防火墙状态：

```bash
sudo ufw status verbose
```

### 7.2 禁用防火墙（可选）

如果你不再需要防火墙，可以禁用：

```bash
sudo ufw disable
```

---

## 第八步：验证卸载完成

### 8.1 检查服务状态

检查所有服务是否已停止：

```bash
sudo systemctl status voluntary-ai-chat
sudo systemctl status nginx
sudo systemctl status mysql
sudo systemctl status redis
```

### 8.2 检查端口占用

检查端口是否已释放：

```bash
sudo netstat -tulpn | grep 8080
sudo netstat -tulpn | grep 80
sudo netstat -tulpn | grep 3306
sudo netstat -tulpn | grep 6379
```

应该看不到任何进程占用这些端口。

### 8.3 检查文件是否已删除

检查文件是否已删除：

```bash
ls /opt/voluntary-ai-chat
ls /etc/nginx/sites-available/voluntary-ai-chat.conf
ls /etc/systemd/system/voluntary-ai-chat.service
```

应该显示：`No such file or directory`

---

## 卸载成功清单

- [ ] 后端服务已停止
- [ ] Systemd服务已删除
- [ ] 应用目录已删除
- [ ] Nginx配置已删除（可选）
- [ ] 数据库已删除（可选）
- [ ] Redis密码已删除（可选）
- [ ] Java已卸载（可选）
- [ ] 防火墙规则已清理（可选）
- [ ] 所有端口已释放
- [ ] 所有文件已删除

---

## 快速卸载脚本

如果你想快速卸载所有内容，可以使用以下脚本：

### 快速卸载脚本（保留MySQL和Redis）

创建卸载脚本：

```bash
vi /tmp/uninstall.sh
```

写入以下内容：

```bash
#!/bin/bash

echo "开始卸载Voluntary AI Chat后端..."

# 停止服务
echo "停止服务..."
sudo systemctl stop voluntary-ai-chat

# 删除Systemd服务
echo "删除Systemd服务..."
sudo systemctl disable voluntary-ai-chat
sudo rm /etc/systemd/system/voluntary-ai-chat.service
sudo systemctl daemon-reload

# 删除应用文件
echo "删除应用文件..."
sudo rm -rf /opt/voluntary-ai-chat

# 删除Nginx配置
echo "删除Nginx配置..."
sudo rm -f /etc/nginx/sites-enabled/voluntary-ai-chat.conf
sudo rm -f /etc/nginx/sites-available/voluntary-ai-chat.conf
sudo systemctl reload nginx

echo "卸载完成！"
echo "MySQL和Redis保留，如需删除请手动执行。"
```

执行卸载脚本：

```bash
chmod +x /tmp/uninstall.sh
/tmp/uninstall.sh
```

### 完全卸载脚本（删除所有内容）

创建完全卸载脚本：

```bash
vi /tmp/uninstall-all.sh
```

写入以下内容：

```bash
#!/bin/bash

echo "开始完全卸载Voluntary AI Chat后端..."

# 停止所有服务
echo "停止所有服务..."
sudo systemctl stop voluntary-ai-chat
sudo systemctl stop nginx
sudo systemctl stop mysql
sudo systemctl stop redis

# 删除Systemd服务
echo "删除Systemd服务..."
sudo systemctl disable voluntary-ai-chat
sudo rm /etc/systemd/system/voluntary-ai-chat.service
sudo systemctl daemon-reload

# 删除应用文件
echo "删除应用文件..."
sudo rm -rf /opt/voluntary-ai-chat

# 删除Nginx
echo "卸载Nginx..."
sudo apt remove nginx -y
sudo apt autoremove -y
sudo rm -rf /etc/nginx

# 删除数据库
echo "删除数据库..."
sudo mysql -e "DROP DATABASE IF EXISTS voluntary_ai_chat;"
sudo mysql -e "DROP USER IF EXISTS 'voluntary_user'@'localhost';"

# 卸载MySQL
echo "卸载MySQL..."
sudo apt remove mysql-server -y
sudo apt autoremove -y
sudo rm -rf /var/lib/mysql
sudo rm -rf /etc/mysql

# 卸载Redis
echo "卸载Redis..."
sudo apt remove redis-server -y
sudo apt autoremove -y
sudo rm -rf /var/lib/redis
sudo rm -rf /etc/redis

# 卸载Java
echo "卸载Java..."
sudo apt remove openjdk-17-jdk -y
sudo apt autoremove -y

# 清理防火墙
echo "清理防火墙..."
sudo ufw delete allow 80/tcp
sudo ufw delete allow 443/tcp
sudo ufw delete deny 8080/tcp
sudo ufw delete deny 3306/tcp
sudo ufw delete deny 6379/tcp

echo "完全卸载完成！"
```

执行完全卸载脚本：

```bash
chmod +x /tmp/uninstall-all.sh
/tmp/uninstall-all.sh
```

---

## 注意事项

1. **数据备份**：卸载前请备份重要数据（数据库、日志等）
2. **谨慎操作**：完全卸载会删除所有内容，无法恢复
3. **保留服务**：建议保留MySQL和Redis，以便后续重新部署
4. **防火墙规则**：卸载后记得清理防火墙规则

---

## 总结

卸载云端部署的核心步骤：

1. **停止服务**：`sudo systemctl stop voluntary-ai-chat`
2. **删除服务**：删除Systemd服务文件
3. **删除文件**：删除 `/opt/voluntary-ai-chat` 目录
4. **清理配置**：删除Nginx配置、防火墙规则
5. **可选卸载**：卸载MySQL、Redis、Java、Nginx

卸载完成后，服务器恢复到部署前的状态。