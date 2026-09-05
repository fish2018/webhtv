# AGENTS.md 有效约束实践评审

日期：2026-08-21

目的：为 WebHTV 的 AI 协作规则确定可执行、低开销、不会牺牲任务完成度的约束方法。

## 结论摘要

公开项目的有效做法不是把 AGENTS.md 写成百科全书，而是把规则写成可判断的动作：明确作用域、唯一推荐命令、禁止的高成本误操作、最小验证路径、生成文件边界和失败分类。规则越接近具体文件/命令/调用方式，越容易执行；“认真检查”“不要浪费时间”这类口号不能形成可靠门禁。

AGENTS.md 仍然只是模型可见的指令层，不能单独强制模型、阻止 `--no-verify`、读取真实剩余上下文 token 或保证自动提交。必须把关键约束分层：AGENTS.md 负责决策顺序和边界，脚本只负责范围/初始状态保护/原子提交/tag，CI 负责组织级强制验证。

## 公开来源与观察

访问日期均为 2026-08-21；通过项目配置的 HTTP(S) 代理访问。

| 来源 | 关键观察 | 对 WebHTV 的可用经验 |
| --- | --- | --- |
| [OpenAI Codex 官方 AGENTS.md 指南](https://learn.chatgpt.com/docs/agent-configuration/agents-md) | 全局、项目根到当前目录逐层加载；更近的文件后加载并覆盖前面的规则；每个目录只取一个 `AGENTS.override.md`/`AGENTS.md`；合并文档默认有 32 KiB 上限；提供“从根目录到专用目录”的分层方法。 | 根规则保持短小；播放器/脚本等高风险目录可增加更具体的规则；必须检查是否被 override 或大小上限截断。 |
| [OpenAI Codex 公共仓库 AGENTS.md](https://github.com/openai/codex/blob/main/AGENTS.md) | 规则具体到命令和代码形态：不要直接运行 `cargo test`，按项目使用 `just test -p`；共享 crate 才扩大测试；避免重复运行；要求改依赖时同步 lock；限制模块和变更行数；要求改动 agent 行为时添加集成测试。 | “最小正确命令”“按影响面扩展”“禁止重复测试”“改动大小触发拆分”比抽象质量口号更有效。 |
| [Vercel Next.js AGENTS.md](https://github.com/vercel/next.js/blob/canary/AGENTS.md) | 明确目录结构和构建入口；按模式选择测试；要求一次捕获完整输出再过滤，禁止换 grep 重跑同一测试；建议 throwaway worktree；根据改动范围选择局部 build 或全量 build；清楚标注环境导致的假错误。 | 先给最小决定性检查；保存输出；区分环境问题和产品回归；大型/不可信操作隔离 worktree。 |
| [Kubernetes AGENTS.md](https://github.com/kubernetes/kubernetes/blob/master/AGENTS.md) | 生成文件只读；明确生成命令和禁止命令（例如不要手工改 `go.mod`、不要 `go mod tidy`）；要求改动聚焦、补测试；提供局部测试和全量验证入口。 | 对 lock、AAR、native 资产、生成代码建立“来源文件 + 生成命令”，禁止手工修产物。 |
| [AGENTS.md 开放格式示例](https://github.com/agentsmd/agents.md/blob/main/README.md) | 最小示例包含环境入口、测试入口、lint 和提交前要求；建议让 agent 直接使用仓库已有命令而不是重新探索。 | 根文件应优先记录项目特有事实和命令，不重复通用编程知识。 |
| [Git hooks 官方文档](https://git-scm.com/docs/githooks) | hook 可由 `core.hooksPath` 改变；`pre-commit`/`commit-msg` 可阻止提交但可用 `--no-verify` 绕过；`post-commit` 发生在提交之后，不能阻止结果；普通仓库不会因为提交一个 hook 文件就自动启用它。 | hook 不能作为唯一强制机制；使用显式 task guard，CI 再做最终门禁；tag 必须由显式脚本在提交后创建。 |
| [GitHub Copilot 仓库指令文档](https://docs.github.com/en/copilot/customizing-copilot/adding-repository-custom-instructions-for-github-copilot) | 仓库级指令和路径级指令可叠加；最近的 `AGENTS.md` 优先；官方建议指令不超过约两页，并记录 bootstrap/build/test/lint 的具体顺序。 | 根 AGENTS.md 控制在短篇幅；路径规则就近放置；把常用命令写全，减少 agent 搜索和试错。 |

## 约束分层

| 目标 | AGENTS.md 能做什么 | 必须自动化的部分 |
| --- | --- | --- |
| 防止需求蔓延 | 要求先写完成句、允许路径、排除项；新行为/模块/依赖边界先停下复盘 | 脚本比较初始和当前 changed paths；超范围返回非零 |
| 快速定位 | 规定“复现/证据 -> 精确调用链 -> 一个假设 -> 最小修复 -> 一次决定性验证” | 单条命令防挂起超时、输出保存、CI 结果留档 |
| 防止无限搜索/测试 | 规定按风险扩展、禁止同命题重复尝试、优先局部命令 | 不使用脚本计数门禁；CI 约束矩阵和并发 |
| 上下文恢复 | 在真实跨会话风险下记录 hash、结果、下一动作 | 脚本记录 branch/HEAD/范围；文档保存 durable state |
| 自动 commit/tag | 要求使用统一 finish 命令，禁止未验证和未提交 tag | 脚本只 stage task-owned paths，`git commit` 后创建唯一 annotated tag |
| 质量不降级 | 明确速度只能删重复工作，不能删关键回归；构建不等于行为正确 | 领域测试、ABI/ELF/设备 CI、发布审批 |

## WebHTV 采用的规则

1. 根 `AGENTS.md` 只保留高优先级合同：短任务默认路径、范围封闭、风险驱动验证、checkpoint、原子 commit/tag、上游 Exo -> MPV 顺序和能力边界。
2. 上游 Skill 负责跨仓库提交台账、证据等级、功能阶段和播放器门禁；继续任务只按需加载 references，避免上下文膨胀。
3. `.codex/scripts/task_guard.sh` 只负责可计算且低开销的安全边界：初始 dirty 文件保护、声明路径、分支/HEAD、staged 范围、原子提交和 recovery tag。
4. 不再设置固定分钟数、文件数量、cycle 计数、强制 checkpoint/replan 或状态目录归档。`check` 仅是按需使用的只读安全审计，`finish` 自行执行最终安全检查。
5. 当前工作区已有的播放器 Java、测试和 lock dirty 修改不纳入治理变更，也不能被自动提交。
6. commit 成功后立即以一次本地、非交互、禁签名的 annotated-tag 命令创建 recovery tag，正常目标不超过 5 秒；commit 与 tag 之间禁止重新测试、构建、联网搜索、反复看 diff 或重复验证 tag。
7. 简单规则/文档/Skill 和局部 bug 都走最短证据路径：必要修改、一次最小验证、获授权时的 commit/tag 和交付；不使用固定分钟节点制造额外动作。
8. 本次低效的直接原因是把固定时间、cycle、文件数量、强制 checkpoint/replan、状态归档和仓库内临时文件都混入安全门禁。现已将守卫收缩为范围与回滚保护，并保留 governance-maintenance fast path。

## 不采用的做法

- 不把每次文件编辑都打 tag；tag 只标记已验证的逻辑提交或阶段恢复点。
- 不把全量测试、全 ABI native 构建和互联网搜索设为所有任务默认步骤。
- 不用 Git hook 假装强制 AI 行为；hook 需要显式安装且可绕过，适合补充检查，不适合承载唯一策略。
- 不用固定分钟数、cycle、文件数量或强制 checkpoint/replan 判断任务进度；完成度由目标、验证和回滚边界决定。
- 不把单条命令的防挂起 timeout 外推成任务 timeout；命令超时后缩小、恢复或换路径继续完成任务。
- 不原样循环失败的 tag 命令；只记录一次错误，修正直接原因后再执行。
- 不对未改变且已成功的文件/命令反复校验；只有相关内容再次修改或首次结果不确定才允许重跑。

## 验证记录

- 官方文档、公开 AGENTS.md、Git hooks/GitHub 指令文档已通过代理直接读取。
- 本次治理 Skill 通过 `skill-creator` 的 `quick_validate.py`。
- `bash -n .codex/scripts/task_guard.sh` 通过。
- `git diff --check` 通过。
- 本轮按效率收口：脚本完成一次 `bash -n`，Skill 完成一次 `quick_validate.py`，仓库完成一次 `git diff --check`；不再为规则文字维护搭建额外测试仓库。
