# 澄远 CRM

给外贸创业团队用的自建客户系统。这一版完成两件事：账号和角色，以及服务端联系人保险库。业务员可以建档、看列表、看详情，但接口和页面都不给可复制的电话、邮箱、WhatsApp 明文。

## 安全规则

- 联系方式只加密存放在服务端保险库。列表、详情、导入结果、审计接口都不向业务员返回明文。
- 经理揭盲必须填写原因，默认 15 分钟，不能续期。开始、结束、过期都写审计。
- 没有导出接口。页面上的联系方式禁选、禁复制，并带有账号水印。
- 公司名、备注、揭盲原因里不能夹带电话或邮箱，避免从旁路把明文带出去。
- 不要把真实密钥写进仓库。`dev` 档案里的密钥是公开占位值，只能用于本机内存库。

## 角色

| 角色 | 账号 | 密码 | 能做什么 |
| --- | --- | --- | --- |
| 业务员 | `sales` | `Sales@123` | 看自己的客户，只能看脱敏联系方式 |
| 经理 | `manager` | `Manager@123` | 看全部客户，可临时揭盲，可看审计 |

自行注册只会成为业务员。请求里即使带了经理角色也会被忽略。

## 本地运行

需要 Java 21 和 Node.js 20 以上。后端用仓库里的 Maven Wrapper，不必单独安装 Maven。

终端一：

```bash
cd backend
./mvnw spring-boot:run
```

后端使用内存 H2，地址 `http://127.0.0.1:18443`。开发文档在 `http://127.0.0.1:18443/swagger-ui.html`。

终端二：

```bash
cd frontend
npm install
npm run dev
```

打开 [澄远 CRM](http://127.0.0.1:43123)。前端把 `/api` 代理到后端。

跑测试：

```bash
cd backend
./mvnw test
```

## 可选 PostgreSQL

```bash
docker compose up -d
export CONTACT_VAULT_KEY="$(openssl rand -base64 32)"
export CONTACT_VAULT_HASH_KEY="$(openssl rand -base64 32)"
export JWT_SECRET="$(openssl rand -base64 48)"
export SPRING_PROFILES_ACTIVE=postgres
cd backend && ./mvnw spring-boot:run
```

`postgres` 档案不会自动灌入演示账号，也不会使用仓库里的占位密钥。数据库口令默认是本地的 `ftcrm_local_only`，正式环境请改掉。变量说明见 `.env.example`。

## 目录

- `backend`：Spring Boot 3.5、Spring Security、JPA、Flyway
- `frontend`：Vue 3、TypeScript、Vite、Element Plus
- `docs/TECH_SPEC_MVP.md`：表结构、接口、安全模型和验收
- `docs/phases`：阶段②④⑤⑥的占位说明
- `docs/M3_STAGE_ABCD_SAMPLES.md`：阶段晋级、流失和 A/B/C/D 样例

## 现在没有的能力

保存客户时还不会改阶段。阶段和等级规则已经可以单测，见 `docs/M3_STAGE_ABCD_SAMPLES.md`。WhatsApp / 邮件发送和沟通存档有 M2 服务端骨架：业务员按客户编号发起，接口返回 501 或空列表，不会真的对外发送，也不会把联系方式明文交回浏览器。30 天公海归属仍未实现。说明见 `docs/M2_MESSAGING_SKELETON.md`。
