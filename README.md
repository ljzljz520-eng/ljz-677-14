# Excel数据导入系统

基于 Spring Boot + Vue 3 的Excel大数据导入系统，支持5万条数据导入及上报国家平台功能。

## 技术栈

- **Frontend**: Vue 3 + Element Plus + Tailwind CSS + Pinia
- **Backend**: Spring Boot 3.2 + MyBatis Plus + EasyExcel
- **Database**: MySQL 8.0
- **Security**: Spring Security + JWT + BCrypt加密

## 核心功能

- Excel文件上传与解析（支持5万条数据，使用EasyExcel SAX模式避免OOM）
- 数据校验与批量导入
- 数据上报国家平台（模拟）
- **国家平台异常结构化回写**：每条异常含行号、医保编号、错误码、错误描述、处理建议，并回写导入任务
- **按错误码筛选异常**：错误码维度聚合计数，支持关键字（医保编号/数据编号/行号）过滤
- **在线修正异常行 + 只重送异常行**：修正后仅重送异常行（支持单条/勾选/一键），已上报成功的数据绝不重复发送
- 异常明细按错误码导出Excel
- 用户登录认证（密码BCrypt加密）

## 启动指南

### 1. 确保 Docker Desktop 已启动

### 2. 在根目录执行

```bash
docker compose up -d --build
```

### 3. 等待容器启动完成（首次构建约3-5分钟）

查看日志：

```bash
docker compose logs -f
```

## 服务地址

| 服务        | 地址                                  |
| ----------- | ------------------------------------- |
| Frontend    | http://localhost:3000                 |
| Backend API | http://localhost:8080                 |
| Swagger文档 | http://localhost:8080/swagger-ui.html |
| Database    | localhost:3306                        |

## 测试账号

| 用户名 | 密码     |
| ------ | -------- |
| admin  | admin123 |

## 项目结构

```
677/
├── backend/                    # Spring Boot后端
│   ├── src/main/java/com/excel/
│   │   ├── config/            # 配置类
│   │   ├── controller/        # 控制器
│   │   ├── dto/               # 数据传输对象
│   │   ├── entity/            # 实体类
│   │   ├── listener/          # EasyExcel监听器
│   │   ├── mapper/            # MyBatis Mapper
│   │   ├── service/           # 服务层
│   │   └── utils/             # 工具类
│   └── Dockerfile
├── frontend/                   # Vue 3前端
│   ├── src/
│   │   ├── api/               # API接口
│   │   ├── assets/            # 静态资源
│   │   ├── components/        # 组件
│   │   ├── router/            # 路由
│   │   ├── stores/            # Pinia状态管理
│   │   └── views/             # 页面
│   └── Dockerfile
└── docker-compose.yml          # 容器编排
```

## API接口

### 认证接口

- `POST /api/auth/login` - 用户登录

### Excel接口

- `POST /api/excel/import` - 导入Excel文件
- `GET /api/excel/records` - 获取导入记录
- `GET /api/excel/data/{batchNo}` - 获取批次数据
- `GET /api/excel/template` - 下载导入模板
- `POST /api/excel/report/{batchNo}` - 上报数据到国家平台（仅发送未成功的数据）
- `GET /api/excel/report/errors/{batchNo}` - 分页查询结构化异常（可按错误码筛选）
- `GET /api/excel/report/error-codes/{batchNo}` - 错误码聚合（筛选用）
- `GET /api/excel/error-code-dict` - 错误码字典（码/描述/处理建议）
- `GET /api/excel/stats/{batchNo}` - 批次上报统计
- `GET /api/excel/row/{id}` - 查询单行（修正回填）
- `PUT /api/excel/data/correct/{id}` - 在线修正异常行（状态回到待重送）
- `POST /api/excel/report/retry-rows/{batchNo}` - 修正后只重送指定异常行
- `POST /api/excel/report/retry/{batchNo}` - 重送批次全部未成功数据
- `GET /api/excel/report/failed/{batchNo}` - 获取上报失败数据（兼容）
- `GET /api/excel/export/errors/{batchNo}` - 导出异常明细（可按错误码导出）

## 数据导入模板

| 字段     | 说明                | 是否必填 |
| -------- | ------------------- | -------- |
| 数据编号 | 唯一标识            | 是       |
| 姓名     | 姓名（最多50字符）  | 是       |
| 身份证号 | 18位身份证号        | 否       |
| 手机号   | 11位手机号          | 否       |
| 金额     | 数值，不能为负      | 否       |
| 地址     | 地址（最多200字符） | 否       |
| 备注     | 备注信息            | 否       |
| 医保编号 | 国家平台医保编号    | 否       |

## 异常处理闭环

1. 上报后国家平台返回的每条异常都会落库到 `report_error`（行号、医保编号、错误码、错误描述、处理建议），并把未处理异常数回写到导入任务；
2. 数据详情页"国家平台异常"页签可**按错误码筛选**（下拉显示每个错误码的异常条数），也可按医保编号/数据编号/行号搜索，不用在几万条里手工找；
3. 点"修正"在线改数据，或直接对平台临时故障（如 E2001/E2002）点"重送"；
4. 重送只发送 `上报失败(2)` 与 `已修正待重送(0)` 的行，`已上报成功(1)` 的行不会被重复发送，支持单条/勾选/一键三种粒度；
5. 异常明细可按当前错误码筛选条件导出 Excel。

## 注意事项

1. 系统使用EasyExcel的SAX模式解析Excel，内存占用低，支持大文件
2. 数据每1000条批量入库，上报成功行按1000条分片单SQL更新，保证性能
3. 上报国家平台为模拟功能，会随机产生5%的失败率用于测试异常处理
4. 密码使用BCrypt加密存储，与数据库密码加密方式一致
