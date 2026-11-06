# 文档索引

| 文档 | 内容 |
| --- | --- |
| [需求规格说明书](01_requirements/湖北工程学院新技术学院AI数字人校园导览系统_需求规格说明书_PRD.md) | 角色、场景、功能与非功能需求 |
| [项目介绍](02-architecture/项目介绍.md) | 技术栈、系统架构、功能、接口、算法、部署和测试 |
| [三维建模说明](03-modeling/README.md) | 地图 2 建模规则、资产、复现命令和验证结果 |
| [校园布局清单](03-modeling/campus-layout.json) | 建筑、点位、道路和镜头预设的结构化坐标 |
| [渲染成果](03-modeling/renders/) | 总览、顶视对齐、分区和网页端截图 |
| [构建与部署约定](04-deployment/maven-settings-aliyun.xml) | 阿里云镜像配置模板（放入 `~/.m2/settings.xml`，全 Maven 入口生效） |
| [提交归档材料](../submission/README.md) | 班级+姓名+(项目名).zip 的归档结构与一键打包 |

## 外部工具说明

`CLI-Anything` 是 Codex 的全局 Skill 和 Blender 自动化工具，不是本项目的业务源码。它应位于用户级目录：

```text
%USERPROFILE%\.codex\skills\cli-anything
%USERPROFILE%\.codex\vendor\CLI-Anything
```

项目仓库只保留 `tools/campus_map2/` 中可复现的建模脚本和最终资产。