# 环境配置指南

> 本文档记录 BladeProject 开发所需的环境配置。

---

## 一、硬件要求

### 后端开发

| 资源 | 最低要求 | 推荐 |
|------|---------|------|
| 内存 | 8GB | 16GB+ |
| CPU | 4核 | 8核+ |
| 硬盘 | 20GB | 50GB+ SSD |

### 移动端开发

| 资源 | 最低要求 | 推荐 |
|------|---------|------|
| 内存 | 8GB | 16GB+ |
| CPU | 4核 | 8核+ |
| 硬盘 | 10GB | 20GB+ |

---

## 二、后端环境

### 2.1 Java 17

**必须使用 JDK 17+**

```bash
# 检查当前 Java 版本
java -version

# macOS 使用 SDKMAN 安装
sdk install java 17.0.13-zulu

# 设置 JAVA_HOME
export JAVA_HOME=/Users/chenjiarun/.sdkman/candidates/java/17.0.13-zulu/zulu-17.jdk/Contents/Home
```

**路径**：`/Users/chenjiarun/.sdkman/candidates/java/17.0.13-zulu/zulu-17.jdk/Contents/Home`

### 2.2 Maven 3.8+

```bash
# 检查 Maven 版本
mvn -version

# macOS 使用 Homebrew 安装
brew install maven
```

### 2.3 MySQL 8.0+

**通过 Docker 运行**（推荐）：

```bash
# 拉取镜像
docker pull mysql:8.0

# 创建容器
docker run -d \
  --name blade-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=blade \
  mysql:8.0
```

**连接信息**：
| 配置 | 值 |
|------|---|
| Host | localhost |
| Port | 3306 |
| Username | root |
| Password | root |
| Database | blade |

**创建数据库**（启动后需执行）：

```bash
docker exec -it blade-mysql mysql -u root -proot -e "CREATE DATABASE IF NOT EXISTS blade DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 2.4 Redis 7.0+

**通过 Docker 运行**（推荐）：

```bash
# 拉取镜像
docker pull redis:7

# 创建容器
docker run -d \
  --name blade-redis \
  -p 6379:6379 \
  redis:7
```

**连接信息**：
| 配置 | 值 |
|------|---|
| Host | localhost |
| Port | 6379 |
| Password | （无密码） |

### 2.5 启动后端

```bash
cd /Users/chenjiarun/Documents/BladeProject/blade-backend

# 编译（需要 Java 17）
JAVA_HOME=/Users/chenjiarun/.sdkman/candidates/java/17.0.13-zulu/zulu-17.jdk/Contents/Home mvn clean compile

# 启动
JAVA_HOME=/Users/chenjiarun/.sdkman/candidates/java/17.0.13-zulu/zulu-17.jdk/Contents/Home mvn spring-boot:run
```

**验证启动成功**：
```bash
curl http://localhost:8080/swagger-ui.html
```

---

## 三、前端环境

### 3.1 Node.js 18+

```bash
# 检查 Node 版本
node -v

# macOS 使用 Homebrew 安装
brew install node

# 或使用 nvm
nvm install 18
nvm use 18
```

### 3.2 npm

```bash
# 检查 npm 版本
npm -v
```

### 3.3 启动移动端

```bash
cd /Users/chenjiarun/Documents/BladeProject/blade-mobile

# 安装依赖
npm install

# 开发模式
npm run dev
```

### 3.4 启动 PC 管理端

```bash
cd /Users/chenjiarun/Documents/BladeProject/blade-admin

# 安装依赖
npm install

# 开发模式
npm run dev
```

---

## 四、Docker 服务一键启动

如果需要同时启动 MySQL 和 Redis：

```bash
# 创建网络
docker network create blade-net

# 启动 MySQL
docker run -d \
  --name blade-mysql \
  --network blade-net \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=blade \
  mysql:8.0

# 启动 Redis
docker run -d \
  --name blade-redis \
  --network blade-net \
  -p 6379:6379 \
  redis:7
```

---

## 五、环境变量速查表

| 变量 | 值 |
|------|---|
| JAVA_HOME | `/Users/chenjiarun/.sdkman/candidates/java/17.0.13-zulu/zulu-17.jdk/Contents/Home` |
| MySQL Host | localhost:3306 |
| Redis Host | localhost:6379 |
| 后端端口 | 8080 |
| 移动端端口 | 5173 |
| PC 管理端端口 | 5173（以 Vite 实际启动端口为准） |

---

## 六、常见问题

### Q: Java 版本不对

```bash
# 查看已安装的 Java 版本
ls /Users/chenjiarun/.sdkman/candidates/java/

# 切换到 Java 17
source ~/.zshrc
sdk use java 17.0.13-zulu
```

### Q: MySQL 连接失败

```bash
# 检查容器是否运行
docker ps | grep blade-mysql

# 查看日志
docker logs blade-mysql
```

### Q: 端口被占用

```bash
# macOS 查看端口占用
lsof -i :3306
lsof -i :6379
lsof -i :8080
lsof -i :5173
```

---

## 七、文档索引

| 文档 | 说明 |
|------|------|
| [01-README.md](./01-README.md) | 项目总览 |
| [开发者必读文档.md](./开发者必读文档.md) | 开发者指南 |
| [02-PRD.md](./02-PRD.md) | 产品需求文档 |
| [03-TASKS.md](./03-TASKS.md) | 开发任务清单 |
