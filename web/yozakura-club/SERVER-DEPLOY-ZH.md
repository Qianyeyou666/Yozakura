# Yozakura Club + OpenClaw 服务器部署

适用于 Ubuntu 22.04/24.04。部署包包含网站和验证服务，但不包含客户端发布包、管理员密钥、网站账号数据或验证用户数据。

## 1. 上传并解压

在本机 PowerShell 执行：

```powershell
scp "Yozakura-Server-Deploy-20260729-account-center.zip" `
  root@服务器IP:/root/Yozakura-Server-Deploy.zip
```

如果你拿到的是其他日期版本，只需替换本地文件名；服务器目标文件名保持 `/root/Yozakura-Server-Deploy.zip`。

服务器执行：

```bash
apt-get update
apt-get install -y unzip
mkdir -p /root/yozakura-deploy
unzip -o /root/Yozakura-Server-Deploy.zip -d /root/yozakura-deploy
cd /root/yozakura-deploy
```

## 2. 一键安装并启动后端

```bash
sudo bash install-all.sh
```

脚本会安装 Java 17、Node.js 22 和运行依赖，创建低权限用户并启用：

```text
openclaw.service       127.0.0.1:8080
yozakura-club.service  127.0.0.1:4173
```

管理员 Token 自动保存在：

```text
/root/yozakura-openclaw-admin-token.txt
```

立即备份到密码管理器，不要放进客户端或公开仓库。JWT Secret 只保存在 `/etc/openclaw/openclaw.env`。

检查后端：

```bash
curl --fail http://127.0.0.1:8080/
curl --fail http://127.0.0.1:4173/api/health
systemctl status openclaw yozakura-club --no-pager
```

## 3. 配置域名和 HTTPS

将下面两个生产域名指向服务器公网 IP：

```text
auth.yozakura.wtf -> OpenClaw 客户端验证与管理后台
yozakura.wtf      -> 网站展示、注册、登录和卡密兑换
```

安装 Caddy：

```bash
apt-get install -y caddy
```

包内 `Caddyfile.yozakura.example` 已写入生产域名，确认 DNS 后安装：

```bash
sudo install -m 0644 Caddyfile.yozakura.example /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl enable --now caddy
sudo systemctl reload caddy
```

如果服务器已有 Caddy 配置，不要覆盖；只追加两个站点块。公网只开放 22、80、443，不开放 4173 和 8080。

访问：

```text
https://auth.yozakura.wtf/admin
https://yozakura.wtf/
```

使用 `/root/yozakura-openclaw-admin-token.txt` 中的值登录验证后台。

## 4. 上传客户端发布包

客户端文件不在部署 ZIP 内。认证修改并完成正式构建后上传：

```powershell
scp "你的客户端发布包.zip" root@服务器IP:/var/lib/yozakura-releases/yozakura-client.zip
```

服务器修正权限并重启网站：

```bash
sudo chown yozakura-club:yozakura-club /var/lib/yozakura-releases/yozakura-client.zip
sudo chmod 0600 /var/lib/yozakura-releases/yozakura-client.zip
sudo systemctl restart yozakura-club
```

文件存在后网站自动开放“登录后下载”。真实文件不在公网目录，未登录直接请求 `/api/client/download` 返回 401，旧 `/downloads/...` URL 返回 404。

如需使用其他路径，编辑：

```bash
sudo nano /etc/yozakura-club/yozakura-club.env
```

修改：

```env
YOZAKURA_CLIENT_FILE=/绝对路径/yozakura-client.zip
```

## 5. 验证服务后台建号

打开验证后台，创建用户名、密码、角色、订阅天数、最大会话数和设备绑定规则。账号在第一次验证成功后才开始订阅计时。

注意：网站账号注册后可通过卡密安全关联 OpenClaw 客户端订阅；Club 只传递密码 verifier，不传递明文密码。网站下载目前要求网站账号登录；套餐支付仍是 `pending_gateway`，尚未作为下载门禁。账户中心中的订阅和 HWID 摘要由 Club 通过内部签名接口从 OpenClaw 查询，浏览器不会直接访问验证服务，也不会收到完整 HWID。

## 6. 更新

先备份：

```bash
sudo cp -a /var/lib/openclaw/users.properties /root/users.properties.backup
sudo cp -a /var/lib/yozakura-club /root/yozakura-club-data.backup
sudo cp -a /etc/openclaw/openclaw.env /root/openclaw.env.backup
```

上传并解压新版后再次运行：

```bash
sudo bash install-all.sh
```

安装器不会覆盖已有环境配置和持久化账号数据。

## 7. 日志与排查

```bash
sudo journalctl -u openclaw -n 100 --no-pager
sudo journalctl -u yozakura-club -n 100 --no-pager
sudo journalctl -u caddy -n 100 --no-pager
sudo ss -lntp | grep -E ':(80|443|4173|8080)\b'
```

常见问题：

- 网站显示“构建中”：`YOZAKURA_CLIENT_FILE` 对应文件不存在或权限不可读。
- 下载返回 401：网站账号未登录或 Token 已过期。
- 云配置换票返回 502：OpenClaw 未运行，或 `/etc/yozakura-club/yozakura-club.env` 中内省地址错误。
- Caddy 502：对应本地服务未启动。
- 客户端验证失败：客户端 Native 地址、HTTPS 域名和 SPKI Pin 尚未与实际证书统一。
