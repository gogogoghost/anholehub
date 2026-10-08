# anlobehub integration contract (lobehub/lobehub canary, Better Auth + tRPC v11 + superjson)

> Confirmed from source (`lobehub/lobehub@canary`, formerly lobe-chat). Base URL = official cloud or user-entered self-hosted address, trailing slash stripped.

## 0. Transport

- tRPC entry: `{base}/trpc/mobile` (GET + POST, mobileRouter, 27 sub-routers).
- query → `GET /trpc/mobile/<p1>,<p2>?batch=1&input={"0":{"json":{...}}}`.
- mutation → `POST /trpc/mobile/<proc>?batch=1`, same body shape (single proc may skip batch: `POST /trpc/mobile/<proc>` with `{"json":{...}}`).
- Response: batch array `[{result:{data:{json,meta?}}}|{error:{json:{code,message}}}]`; single object otherwise.
- superjson envelope: `{json, meta:{values:{<jsonpath>:[Date|Map|Set|...]}}}`. Date inputs must carry meta (e.g. `message.getMessages.before.createdAt`).
- Auth: cookie session (Better Auth), OkHttp CookieJar scoped per base-URL host.

## 1. Sign-in (Better Auth)

- `POST /api/auth/sign-in/email` body `{email*, password*(8~64), rememberMe?, callbackURL?}` → 200 + Set-Cookie + `{user, session}`.
- `GET /api/auth/get-session` (with cookies) → `{user, session}`; used for startup restore/validation.
- `POST /api/auth/sign-out`, empty body OK.

## 2. Agents / topics / messages (agent-first; sessions are a deprecated shell)

- `session.getGroupedSessions()` → `{sessionGroups, sessions[]}`. `@deprecated`: kept only for the legacy mobile session list; new code must not write sessions.
- `agent.queryAgents{includeInbox?, keyword?, limit?(≤100), offset?}` → minimal rows `{id, name, title, description, avatar, backgroundColor, isInbox?}`. Inbox (Lobe AI) only with `includeInbox:true`.
- `agent.getAgentConfigById{agentId*}` → full merged config (systemRole, model, provider, params, ...). `agent.createAgent{config?, ...}` → `{agentId}`; `agent.updateAgentConfig{agentId*, value}` (partial passthrough); `agent.removeAgent{agentId*}` (recycle bin); `agent.duplicateAgent{agentId*, newTitle?}`.
- `topic.getTopics{agentId?, sessionId?(legacy), ...}` → `{items, total}`; `topic.createTopic{title*, ...basicContext}` → id; `topic.removeTopic{id*}`. New topics carry `agentId` directly (agentId > sessionId priority).
- `message.getMessages{topicId?, agentId?, sessionId?(legacy), ...}` → `UIChatMessage[]`. `createMessage{role*, content*, topicId?, ...}`.
- UIChatMessage: `id*/content*/role*(14 kinds)/createdAt,updatedAt:number/parentId?/topicId?/model?/provider?/error?/tools?/fileList?/usage?`, etc.

## 3. Send & stream

- Persistence (optional; mobile uses execAgent in one step): `aiChat.sendMessageInServer{newUserMessage.content*, newAssistantMessage?, topicId?, ...}` → `{assistantMessageId, userMessageId, topicId, isCreateNewTopic, ...}`.
- Execution: `aiAgent.execAgent{prompt*, agentId|slug, appContext{topicId?,...}, clientProtocol:2, ...}` → `{operationId*, token*(5m JWT), topicId*, userMessageId*, assistantMessageId*, ...}`.
- Related: `issueGatewayUserToken()` → `{token}` (v2); `refreshGatewayToken{topicId*}` → `{token}` (v1 reconnect); `interruptTask{operationId?|threadId?}` stops.
- Gateway address: `config.getGlobalConfig` → `serverConfig.agentGatewayUrl` + `agentGatewayProtocol` (1|2, default 1).
- **Gateway is WS, no SSE**:
  - v1: `{gw}/ws?operationId=`; open → `{"type":"auth","token"}` → auth_success → `{"type":"resume","lastEventId","wantStatus":true}` → agent_event; 30s heartbeat; `tool_result` replies.
  - v2: `{gw}/v2/ws?token&clientId`; wait for ready → per-op `subscribe{operationId,...}`; terminal = own-op `agent_runtime_end`/non-waitable error/terminal status.
- 22 event types; core flow: `stream_start{assistantMessage{id...}}` → `stream_chunk{chunkType,content?,reasoning?...}` → `stream_end` → `agent_runtime_end`.

## 4. Models & user

- `aiProvider.getAiProviderRuntimeState{isLogin?}` → full runtime state (primary model source).
- `aiProvider.getAiProviderList()`; `aiModel.getAiProviderModelList{id*, ...}`.
- `user.getUserState()` → `{userId?, avatar?, email?, fullName?, preference*, settings*, ...}`.

## 5. Upload (no REST /api/v1/files)

1. `file.checkFileHash{hash*}` → `{isExist, url?}`.
2. `upload.createS3PreSignedUrl{pathname*, size?}` → presigned URL (3-step multipart for large files).
3. Client PUTs bytes straight to S3.
4. `file.createFile{name*, hash*, fileType*, size*, url*(=pathname), ...}` → `{id, url}`.

## 6. Minimal loop

sign-in → get-session → getGlobalConfig → getUserState/getAiProviderRuntimeState → getTopics/getMessages → execAgent(clientProtocol:2) → gateway WS chunks → interruptTask → upload chain → sign-out.
