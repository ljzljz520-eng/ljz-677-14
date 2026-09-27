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
- **上报异常闭环处理**：
  - 国家平台返回异常时，异常结果结构化写回导入任务（import_record）
  - 每条异常包含：行号、医保编号（数据编号）、错误码、错误描述、处理建议
  - 前端异常明细视图支持按错误码筛选（SQL级筛选+分页，无需在几万条中手工查找）
  - 支持在线修正异常数据，修正后仅重送异常行，已上报成功数据不重复发送
  - 异常数据可导出Excel（含行号、错误码、处理建议）
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
- `POST /api/excel/report/{batchNo}` - 上报数据到国家平台
- `GET /api/excel/report/failed/{batchNo}` - 获取上报失败数据
- `GET /api/excel/report/errors/{batchNo}?errorCode=&pageNum=&pageSize=` - 分页查询上报异常（支持按错误码筛选，返回错误码统计）
- `POST /api/excel/report/retry/{batchNo}` - 重试上报（仅重送异常行）
- `PUT /api/excel/data/{id}` - 修正异常行数据
- `GET /api/excel/export/errors/{batchNo}` - 导出异常数据（含行号/错误码/处理建议）

## 上报异常处理流程

```
上报国家平台
    ↓ 平台返回异常
异常结果写回导入任务（import_record.report_error_details，JSON格式）
    ↓
每条异常：行号 + 医保编号 + 错误码 + 错误描述 + 处理建议
    ↓
前端异常明细视图 → 按错误码筛选（SQL级筛选+分页）
    ↓
在线修正数据 / 导出异常Excel线下修正
    ↓
重送异常行（仅发送失败的异常行，已成功数据不重复发送）
```

### 错误码说明

| 错误码 | 错误描述 | 处理建议 |
| ------ | -------- | -------- |
| E1001 | 数据格式不符合规范 | 检查字段格式，修正后重新上报 |
| E1002 | 重复数据已存在 | 确认是否重复导入 |
| E1003 | 身份证号校验失败 | 核对18位身份证号及校验位 |
| E1004 | 手机号格式错误 | 核对11位手机号 |
| E1005 | 金额超出限额 | 核对金额，必要时拆分 |
| E2001 | 平台服务暂时不可用 | 无需修改数据，稍后直接重试 |
| E2002 | 数据校验超时 | 无需修改数据，稍后直接重试 |
| E9999 | 系统异常 | 联系系统管理员 |

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

## 注意事项

1. 系统使用EasyExcel的SAX模式解析Excel，内存占用低，支持大文件
2. 数据每1000条批量入库，保证性能
3. 上报国家平台为模拟功能，会随机产生5%的失败率用于测试异常处理（70%为数据类错误E1xxx，30%为平台服务类错误E2xxx）
4. 密码使用BCrypt加密存储，与数据库密码加密方式一致
5. 上报异常写回 `import_record.report_error_details`（MEDIUMTEXT，JSON格式），已有环境升级请执行 `schema.sql` 注释中的 ALTER 语句
