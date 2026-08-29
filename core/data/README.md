# core/data

种子/参考数据文件，供本地开发与测试导入，均不含真实个人信息。

| 文件 | 对应 PRD | 说明 |
|---|---|---|
| `mobile_location_sample.csv` | F-5.7 / 10.4 节 `mobile_locations` | 号段归属地库样例（仅覆盖少量号段，生产数据需从工信部/运营商公开号段表批量导入） |
| `sensitive_words_sample.csv` | F-5.5 / 10.5 节 `sensitive_words` | 内容审核词库样例 |
| `status_code_mappings_sample.csv` | F-13.3 / 10.10 节 `status_code_mappings` | 通道状态码 -> 平台统一状态码映射样例 |

这些文件不会被 Flyway 自动导入——正式的种子数据导入脚本是 TODO（见 ../docs/ROADMAP.md），
当前可用 `LOAD DATA LOCAL INFILE` 手工导入验证。
