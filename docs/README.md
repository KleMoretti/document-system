# 文档目录

本目录分为两类文档：工程维护文档和学习面试材料。工程维护文档用于开发、联调、排障、压测和契约维护；学习面试材料用于梳理项目表达，不作为实现契约来源。

## 工程维护文档

- [架构说明](architecture.md)：系统边界、协同编辑链路、高并发方案和主要产品能力。
- [接口契约说明](api-contract.md)：Java REST、WebSocket、角色、快照、版本、评论等行为约定。
- [运维与压测说明](operability.md)：健康检查、指标、WebSocket 压测方法、高并发调优项和压测报告模板。
- [前端与 Java 后端实现详解](frontend-java-backend-guide.md)：前端、Java 后端和端到端业务链路的代码级说明。
- [安全说明](security-notes.md)：认证、WebSocket 令牌、Redis 和部署安全注意事项。

接口、WebSocket 消息和 SQL schema 的事实来源仍在 `packages/shared-contract/`。当 Java 实现行为发生变化时，应同步契约文件和本目录中的说明文档。

## 学习与面试材料

- [Java 全栈简历学习路线](java-fullstack-resume-roadmap.md)：按阶段学习和复盘项目。
- [Java 全栈简历问答与面试官追问](java-fullstack-resume-qa.md)：面试问答式项目表达材料，包含面试官深挖题、评分参考和回答模板。

这些材料可以引用工程文档中的事实，但不要反向作为接口、部署或安全决策依据。
