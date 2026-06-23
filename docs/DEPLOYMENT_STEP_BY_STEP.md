# 部署教程（傻子也能看懂版）

> 如果你看到任何看不懂的词，直接跳过别管，按步骤操作就行。

---

## 你需要准备的东西

1. **一台云服务器**（就是一台永远不关机的电脑）
   - 去阿里云/腾讯云买一台，最便宜的就行（2核4G）
   - 系统选 **Ubuntu 20.04**（别问我这是什么，选就对了）

2. **一个域名**（不是必须的，但没有的话功能不全）
   - 比如 `wo-de-chat.com`
   - 去阿里云/腾讯云买一个，一年几十块钱

---

## 第一步：在你自己电脑上打包

打开 PowerShell（就是那个蓝底白字的窗口），输入：

```powershell
cd D:\voluntary_AI_chat
mvn clean package -pl server -am '-Dcheckstyle.skip=true' '-DskipTests'
```

等它跑完，看到 **BUILD SUCCESS** 就对了。

然后输入：

```powershell
Get-ChildItem server\target\*.jar
```

你会看到一堆文件，找到名字带 **exec** 的那个（大概50MB），它就是我们要的。

---

## 第二步：连接云服务器（重点！）

### 2.1 你需要一个叫"SSH"的东西

Windows 10/11 自带了，不用装任何东西。

打开 PowerShell，输入：

```powershell
ssh root@你的服务器IP
```

> **什么是服务器IP？** 就是你买服务器时，商家给你的一串数字，比如 `123.456.789.0`
> 
> **什么是 root？** 就是超级管理员账号，买服务器时商家会让你设密码的那个

第一次连接会问你：

```
Are you sure you want to continue connecting?
```

输入 `yes` 回车。

然后输入你的服务器密码（输入时屏幕不会显示任何东西，正常现象），回车。

看到这个就说明连上了：

```
root@你的服务器名:~#
```

---

## 第三步：安装 Java（就是让程序能运行的东西）

连上服务器后，复制下面一整段，粘贴到黑窗口里，回车：

```bash
apt update
apt install openjdk-17-jdk -y
```

等它跑完就行（大概1分钟）。

验证一下：

```bash
java -version
```

看到 `openjdk version "17"` 就对了。

---

## 第四步：安装 MySQL（存聊天记录的地方）

复制粘贴：

```bash
apt install mysql-server -y
```

等它跑完。

然后输入：

```bash
mysql
```

你会看到光标变成 `mysql>` 或者 `MariaDB [(none)]>`（不同版本显示不一样，没关系）。

把下面这段复制粘贴进去（**这是创建数据库，别怕，系统自动执行的**）：

```sql
CREATE DATABASE voluntary_ai_chat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'voluntary_user'@'localhost' IDENTIFIED BY '9sK$7pR2&zQ5!dF8';
GRANT ALL PRIVILEGES ON voluntary_ai_chat.* TO 'voluntary_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

看到 `Query OK` 就对了（可能有好几个）。

---

## 第五步：安装 Redis（存临时数据用的）

复制粘贴：

```bash
apt install redis-server -y
```

等它跑完。

---

## 第六步：创建文件夹（放程序的地方）

复制粘贴：

```bash
mkdir -p /opt/voluntary-ai-chat
mkdir -p /opt/voluntary-ai-chat/logs
mkdir -p /opt/voluntary-ai-chat/uploads/chat/images
```

不用管它，执行完就行。

---

## 第七步：把程序传到服务器上

**这一步要在你的本地电脑上操作，不是在服务器上！**

先按 `Ctrl + D` 退出服务器连接，回到你的本地电脑。

在本地 PowerShell 输入：

```powershell
scp server\target\voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar root@你的服务器IP:/opt/voluntary-ai-chat/
```

会提示输入密码，输入你的服务器密码。

等它传完（大概几十秒到几分钟）。

---

## 第八步：再次连接服务器

```powershell
ssh root@你的服务器IP
```

输入密码。


---

## 第九步：创建配置文件

复制下面一整段，粘贴到黑窗口：

```bash
cat > /opt/voluntary-ai-chat/config.env << 'EOF'
export DB_HOST=localhost
export DB_USER=voluntary_user
export DB_PASSWORD=9sK$7pR2&zQ5!dF8
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=
export JWT_SECRET=random_secret_key_123456
export CORS_ALLOWED_ORIGINS=*
export UPLOAD_BASE_URL=http://你的服务器IP:8080/files
EOF
```

> **特别提醒：** 把上面命令里的 `你的服务器IP` 替换成你实际的 IP 地址。

---

## 第十步：启动程序

```bash
cd /opt/voluntary-ai-chat
nohup java -jar voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar --spring.profiles.active=cloud --server.address=0.0.0.0 > logs/server.log 2>&1 &
```

看到输出了一个数字（比如 `12345`），就说明启动成功了。

查看日志确认：

```bash
tail -f logs/server.log
```

看到这句话就成功了：

```
Started VoluntaryAiChatApplication in x.xxx seconds
非热点模式：服务器启动完成，不启动广播服务
```

按 `Ctrl + C` 退出日志查看。

---

## 第十一步：配置防火墙（安全措施）

复制粘贴：

```bash
ufw allow ssh
ufw allow 8080/tcp
ufw enable
```

系统会问：

```
Command may disrupt existing ssh connections. Proceed with operation (y|n)?
```

输入 `y` 回车。

---

## 第十二步：测试是否成功

在你本地的浏览器里输入：

```
http://你的服务器IP:8080/api/auth/test
```

如果看到一堆英文（JSON格式），说明成功了！

---

## 第十三步：在本地客户端连接

打开你的客户端程序，设置为云端模式：

设置方式：在 `server` 模块的配置中，将 `--spring.profiles.active=cloud` 作为启动参数。

你的服务器地址填：

```
http://你的服务器IP:8080/api
```

---

## 如果出问题了怎么办

### 情况1：连不上服务器

检查服务器是不是没开机，或者 IP 写错了。

### 情况2：启动后看不到成功日志

```bash
cat /opt/voluntary-ai-chat/logs/server.log
```

看看最后几行写的什么，把内容告诉我。

### 情况3：浏览器访问不了

```bash
ufw status
```

看看有没有显示 `8080/tcp ALLOW`

---

## 总结（不需要记，看一遍就行）

1. 买服务器 → 选 Ubuntu
2. 连上服务器 → 装 Java、MySQL、Redis
3. 本地打包程序 → 传到服务器
4. 配置并启动程序
5. 开放防火墙端口
6. 本地客户端连接

> **实在看不懂？** 打开远程桌面，我一步一步教你。