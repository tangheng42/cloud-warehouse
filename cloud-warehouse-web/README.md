# 云仓智配管理后台（第一阶段）

为「云仓智配 —— 多仓库存与调拨协同平台」Java 后端提供 PC 管理界面。接口以 [`../docs/yuncang-openapi.yaml`](../docs/yuncang-openapi.yaml) 为准，页面使用真实后端数据，不包含 Mock。

## 技术栈与环境

- Vue 3、Vite 7、TypeScript、Vue Router、Pinia
- Element Plus、Axios
- Node.js 20.19+ 或 22.12+，推荐 24.x；npm 随 Node.js 安装

## 启动

先启动本仓库 Java 后端，默认监听 `http://localhost:8080`，然后在当前目录执行：

```bash
npm install
npm run dev
```

打开开发服务器显示的地址，通常是 `http://localhost:5173`。生产构建：

```bash
npm run build
```

`vite.config.ts` 将浏览器访问的 `/api/v1/**` 原样代理到 `http://localhost:8080`，因此开发时无须在各页面配置后端地址。若后端运行在其他地址，可设置 `VITE_PROXY_TARGET`，例如 `VITE_PROXY_TARGET=http://localhost:8081`。已部署的前后端若不使用同源 `/api/v1`，可用 `VITE_API_BASE_URL` 指定完整 API 基地址。不要将数据库密码或 JWT 密钥写进前端环境变量。

## 页面与权限

| 路由 | 功能 |
| --- | --- |
| `/login` | 登录，成功后获取当前用户 |
| `/dashboard` | 已开放功能入口，不展示虚构统计 |
| `/warehouses` | 仓库查询、详情、新增、编辑、启用、停用 |
| `/sku-categories` | 分类查询、详情、新增、编辑、启用、停用 |
| `/skus` | SKU 查询、详情、新增、编辑、启用、停用；分类可搜索选择 |
| `/stocks` | 库存分页与指定仓库 SKU 详情，仅查询 |

基础资料的写入按钮仅向 `ADMIN` 展示，最终权限以 Java 后端校验为准。入库、出库、调拨、库存盘点、库存流水和库存预警仅在侧栏标注“开发中”，没有页面或虚构接口。

## 目录

```text
src/
├── api/          # 按业务拆分的后端接口
├── layouts/      # 后台框架与侧边栏
├── router/       # 路由与登录守卫
├── store/        # JWT 和当前用户状态
├── styles/       # 全局样式
├── types/        # 与 OpenAPI 对应的 DTO/VO
├── utils/        # Axios 统一响应处理与格式化
└── views/        # 六个第一阶段页面
```

登录 token 保存在浏览器 `localStorage`，请求拦截器统一添加 `Authorization: Bearer <token>`。后端明确返回 401 或 `UNAUTHENTICATED` 时清理会话并返回登录页；其他业务错误展示后端 `message`。库存 API 模块只有 GET 方法，界面无库存数量修改入口。
