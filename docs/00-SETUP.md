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
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=blade_project \
  mysql:8.0
```

**连接信息（当前本机 Docker MySQL）**：
| 配置 | 值 |
|------|---|
| Host | localhost |
| Port | 3306 |
| Username | root |
| Password | root123 |
| 开发库 | blade_project |
| 本地生产库 | blade_project_prod |

当前后端默认连接**开发库** `blade_project`。本地生产库 `blade_project_prod` 保留，用于真实/演示数据隔离；开发时不要默认连接生产库。

**当前验证状态（2026-06-04）**：

| 数据库 | 用途 | Flyway 状态 |
|--------|------|-------------|
| `blade_project` | 开发库，后端默认连接 | 当前容器内已存在，已自动迁移到 V36 |
| `blade_project_prod` | 本地生产/真实录入库 | 当前容器内已存在，核对时到 V36 |

**后端连接规则**：

| 场景 | 命令 |
|------|------|
| 默认开发库 | `mvn spring-boot:run` |
| 显式连接开发库 | `BLADE_DB_URL='jdbc:mysql://localhost:3306/blade_project?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true' BLADE_DB_USERNAME=root BLADE_DB_PASSWORD=root123 mvn spring-boot:run` |
| 临时连接本地生产库 | `BLADE_DB_URL='jdbc:mysql://localhost:3306/blade_project_prod?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true' BLADE_DB_USERNAME=root BLADE_DB_PASSWORD=root123 mvn spring-boot:run` |

直接登录数据库：

```bash
# 查看两个库
docker exec -it blade-mysql mysql -u root -proot123 -e "SHOW DATABASES LIKE 'blade_project%';"

# 进入开发库
docker exec -it blade-mysql mysql -u root -proot123 blade_project

# 进入本地生产库
docker exec -it blade-mysql mysql -u root -proot123 blade_project_prod
```

**创建数据库**（启动后需执行）：

```bash
docker exec -it blade-mysql mysql -u root -proot123 -e "CREATE DATABASE IF NOT EXISTS blade_project DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE IF NOT EXISTS blade_project_prod DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
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

PC 管理端当前固定端口为 `5777`，Vite 已配置 `host: 0.0.0.0`，可被同一局域网内的 iPad 访问。

### 3.5 iPad Catalog PWA 调试

客户选款页路径：`/catalog`。

本机 Mac 当前局域网访问格式：

```bash
# 查看 Mac 当前 Wi-Fi IP
ipconfig getifaddr en0

# 当前示例
http://192.168.1.3:5777/catalog
```

iPad 调试步骤：

1. Mac 启动 MySQL、Redis、后端和 `blade-admin` 前端。
2. iPad 与 Mac 连接同一个 Wi-Fi。
3. 在 iPad Safari 打开 `http://<Mac局域网IP>:5777/catalog`。
4. 登录具备 `data:catalog:view` 权限的账号。
5. Safari 点击分享按钮，选择“添加到主屏幕”。
6. 从 iPad 桌面图标打开“现货选款”，此时会以接近 App 的独立窗口运行，不显示 Safari 地址栏和标签栏。

注意：
- 开发环境使用 HTTP 局域网地址，适合真机 UI/触控调试；Service Worker 离线缓存、推送等高级 PWA 能力通常需要 HTTPS，后续部署到正式域名时再启用。
- 如果 iPad 无法访问，优先检查 Mac 防火墙是否拦截 Node/Vite、iPad 与 Mac 是否在同一 Wi-Fi、路由器是否开启 AP 隔离。
- 通过 Vite 访问时，`/api` 会由 Mac 上的 Vite 代理到 `localhost:8080` 后端，iPad 不需要直接访问后端 8080。

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
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=blade_project \
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
| MySQL Username | root |
| MySQL Password | root123 |
| 后端默认数据库 | blade_project |
| 本地生产数据库 | blade_project_prod |
| 数据库覆盖变量 | `BLADE_DB_URL` / `BLADE_DB_USERNAME` / `BLADE_DB_PASSWORD` |
| Redis Host | localhost:6379 |
| 后端端口 | 8080 |
| 移动端端口 | 5173 |
| PC 管理端端口 | 5777 |
| iPad Catalog 测试地址 | `http://<Mac局域网IP>:5777/catalog` |

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

# 验证密码和数据库
docker exec -it blade-mysql mysql -u root -proot123 -e "SHOW DATABASES LIKE 'blade_project%';"
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
