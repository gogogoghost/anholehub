# PWA parity checklist (v1 scope)

Based on the 27 mobileRouter sub-routers + visible official PWA mobile features:

| # | Feature | tRPC/API | Lands in |
|---|---------|----------|----------|
| 1 | Startup session restore (cookie→get-session→straight to sessions) | BetterAuthApi.getSession | app NavHost dynamic startDestination |
| 2 | Model picker (switch model/provider on chat screen) | ModelRepository.listModels | feature:chat top-bar dropdown |
| 3 | Markdown rendering (incl. code blocks) | Local | designsystem MarkdownText (mikepenz) |
| 4 | Create/delete sessions | SessionRepository create/delete | feature:sessions FAB + delete |
| 5 | Topic (sub-chat) list/create/delete | TopicRepository | feature:chat drawer |
| 6 | Settings: account info/sign-out/server address/switch server | AuthRepository/UserRepository | feature:settings |
| 7 | User info | user.getUserState | core:data UserRepository |
| 8 | File upload (send images/docs) | upload.createS3PreSignedUrl + PUT + file.createFile | core:data FileRepository + chat attach button |
| 9 | Knowledge-base list (read-only browse) | knowledgeBase router | DiscoveryRepository, probe `knowledgeBase.getKnowledgeBases`, empty list + hint on failure |
| 10 | Assistant (agent) list | agent router (`agent.getAgents` probe) | Same, read-only |

Non-v1 (PWA has it, deferred): MCP/plugin execution, TTS, marketplace install, PWA push, passkey/SSO sign-in, group/multi-agent orchestration.
