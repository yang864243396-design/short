# 后端在宝塔（测试机）部署 — 详细步骤

本文假设：API 域名为 `http://api2.h6ign.com`，反代到本机 **`127.0.0.1:8080`**。

## 目录说明（与你当前服务器一致）

宝塔网站根常为 **`/www/wwwroot/short/`**，其中 **Go 项目放在子目录 `backend/`** 下，即：

| 用途 | 路径 |
|------|------|
| **工作目录**（含 `go.mod`、`main.go`、`.env`、二进制 `short`） | **`/www/wwwroot/short/backend/`** |
| 可执行文件 | **`/www/wwwroot/short/backend/short`** |
| 环境变量 | **`/www/wwwroot/short/backend/.env`** |
| 上传目录 | **`/www/wwwroot/short/backend/uploads/`**（程序里相对路径 `./uploads`） |

`godotenv.Load()` 与静态资源路径都依赖**进程当前工作目录**，因此 **Supervisor 的「运行目录」必须填**：

```text
/www/wwwroot/short/backend
```

**启动命令**：

```text
/www/wwwroot/short/backend/short
```

不要在「运行目录」填 `/www/wwwroot/short` 而二进制在 `backend` 里，否则找不到 `.env`，上传路径也会错。

---

## 一、宝塔依赖

1. 安装：**Nginx**、**MySQL**、**Redis**、（建议）**Supervisor**。
2. `redis-cli ping` 返回 `PONG`。

## 二、数据库

宝塔 → 数据库 → 建库（如 `short`），记下账号密码，在 **`backend/.env`** 里配置 `DB_*`。

## 三、`.env`

在 **`/www/wwwroot/short/backend/`** 下：

```bash
cd /www/wwwroot/short/backend
cp .env.example .env   # 若仓库里有示例
nano .env
```

至少配置：`GIN_MODE`、`HTTP_PORT=8080`、`DB_*`、`REDIS_*`、`JWT_SECRET`，邮件再配 `SMTP_*`。

### 邮箱验证码（用户注册「获取验证码」）

须在同一 `.env` 中配置完整：**`SMTP_HOST`、`SMTP_PORT`、`SMTP_USER`、`SMTP_PASSWORD`、`SMTP_FROM`**。缺任一项或服务连不上时，接口会报错或「邮件发送失败」。

- **`SMTP_FROM`**：发件人地址，多数邮箱要求与 **`SMTP_USER`** 一致（或为其已验证别名）。
- **端口**：`465` 为 SSL；`587` 为 STARTTLS（QQ/163/企业邮箱常用；若云主机封 465，可改为 `587`）。
- **出站**：服务器需能访问外网 SMTP（防火墙/安全组放行 **出站** `465` 或 `587`）。
- **排查**：改配置后重启进程；看 **`Supervisor` / 系统日志** 中带 `[SendRegisterCode]` 的行，内有具体 SMTP 错误。

## 四、编译（推荐：本机编译，服务器不装 Go）

**服务器上不必安装 Go**，只在你的开发机编译出 Linux 二进制后上传即可。

### Windows（PowerShell）

在仓库 **`backend`** 目录（与 `go.mod` 同级）执行：

```powershell
cd E:\CompanyCode\ai\short\backend
$env:GOOS="linux"
$env:GOARCH="amd64"
go build -o short .
```

生成无后缀文件 **`short`**。用宝塔文件管理器或 SFTP 上传到：

**`/www/wwwroot/short/backend/short`**（覆盖旧文件）。


## 五、Supervisor

| 项 | 值 |
|----|-----|
| 运行目录 | **`/www/wwwroot/short/backend`** |
| 启动命令 | **`/www/wwwroot/short/backend/short`** |

### 日志在哪看

- **宝塔**：软件商店 → **Supervisor** → 找到 `short` 任务 → **日志**（或「查看日志」）。
- **命令行**：常见为 **`/www/server/panel/plugin/supervisor/log/`** 下，或你在 Supervisor 配置里为 `stdout_logfile=` / `stderr_logfile=` 指定的路径。
- 程序用 **`log.Printf` / `log.Fatalf`** 会进 **标准错误**，请确认 Supervisor **把 stderr 也写入日志**（多数模板会合并到同一文件）。

新版本启动时会打印 **`Redis 已连接 addr=...`**；若 **`Redis 连接失败`** 进程会直接退出，Supervisor 日志里必有整行说明（多为 `.env` 里 **`REDIS_ADDR` / `REDIS_PASSWORD`** 与宝塔 Redis 不一致）。

### 用户端提示「服务繁忙」/「验证码服务暂不可用」

接口 **`/auth/send-register-code`** 需先 **向 Redis 写入验证码**。若 Redis 连不上，会返回上述提示。请先按上文确认 **Redis 已连接**（启动日志），并在服务器执行：`redis-cli -a '你的密码' ping` 应返回 **`PONG`**，且 `.env` 中 **`REDIS_ADDR`** 一般为 **`127.0.0.1:6379`**（本机 Redis）。

若出现 **`gave up: short_00 entered FATAL state`**（反复 `exit status 1`），说明进程**启动阶段就崩了**，常见原因：

1. **MySQL 连不上 / 迁移失败** — 核对 `.env` 里 `DB_*`。  
2. **Redis 连不上** — 启动时会 **`Ping` Redis**，失败则**直接退出**（日志里有 **`Redis 连接失败 addr=... err=...`**）。请核对 **`REDIS_ADDR`**（多为 `127.0.0.1:6379`）、**`REDIS_PASSWORD`** 与宝塔 Redis 一致，并执行 `redis-cli -a 密码 ping` 得到 **`PONG`**。  
3. **工作目录错误**导致没加载 `.env` — 日志里会有 **`working_directory=...`**，必须是含 **`go.mod`、`.env` 的 `backend` 目录**。

程序会把 **`log` 打到 stdout**，并**追加写入**运行目录下的 **`backend_startup.log`**（与 `go.mod` 同目录）。若 Supervisor 界面只有 `exit status 1` 没有具体原因，用宝塔 **文件管理器** 打开 **`/www/wwwroot/short/backend/backend_startup.log`** 看最后一屏（含 `Redis 连接失败` / `Failed to connect database` 等）。

若 Supervisor 里仍看不到程序输出，请为该任务配置 **`stdout_logfile`/`stderr_logfile`** 后 **`supervisorctl reread` + `update`**。

保存后启动，再执行：

```bash
ss -tlnp | grep 8080
curl -v --max-time 5 "http://127.0.0.1:8080/api/v1/health"
```

## 六、Nginx（api2 站点）

整站反代到 `http://127.0.0.1:8080`，勿用静态目录解析 `/api/v1`。示例见仓库 **`nginx-api2.example.conf`**。

## 七、常见问题

| 现象 | 处理 |
|------|------|
| `ss` 看不到 8080 | 检查 Supervisor 运行目录是否为 **`.../short/backend`**，启动命令是否指向该目录下的 **`short`** |
| 进程秒退 | SSH 执行 `cd /www/wwwroot/short/backend && ./short` 看报错（多为 MySQL/Redis） |
| 404（域名访问） | Nginx 未反代到 8080，仍当静态站 |

---

发版：在本机 **`go build`** 出新 **`short`** → 上传覆盖 → **`chmod +x`** → 重启 Supervisor（服务器不编译）。
