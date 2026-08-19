.PHONY: install run test lint build dev dev-full dev-full-down dev-full-logs

# ---- Backend ----
backend-install:
	cd backend && mvn clean install -DskipTests

backend-run:
	cd backend && mvn spring-boot:run

backend-test:
	cd backend && mvn test

backend-coverage:
	cd backend && mvn test jacoco:report

backend-coverage-check:
	cd backend && mvn test jacoco:check

# ---- Frontend ----
frontend-install:
	cd frontend && npm install

frontend-run:
	cd frontend && npx vite --port 3000

frontend-build:
	cd frontend && npm run build

# ---- Full Stack ----
install: backend-install frontend-install

test: backend-test

lint:
	cd backend && mvn checkstyle:check || true

# ---- CI Pipeline Simulation ----
ci: backend-test backend-coverage-check
	@echo "✅ CI pipeline passed — coverage gate OK"

# ---- Quick Start ----
run: backend-run

# ---- Dev: 起 Kafka + 后端（等就绪）+ 前端（Ctrl+C 一起停止）----
dev:
	@echo ">> 清理端口 8080/3000(如有占用)..."; for p in 8080 3000; do PIDS=$$(lsof -ti:$$p); if [ -n "$$PIDS" ]; then echo "  释放 :$$p ($$PIDS)"; kill $$PIDS 2>/dev/null; fi; done; pkill -f "spring-boot:run" 2>/dev/null || true; sleep 2
	@echo ">> 启动 Kafka (docker compose) ..."
	@docker compose up -d kafka
	@echo ">> 等待 Kafka 就绪 (9094，最多 60s) ..."; n=0; until nc -z localhost 9094 2>/dev/null; do sleep 1; n=$$((n+1)); if [ $$n -ge 60 ]; then echo "❌ Kafka 60s 未就绪（是否已 docker pull apache/kafka:3.7.1）"; exit 1; fi; done; sleep 2; echo ">> Kafka 已就绪"
	@set -m; \
	echo ">> 启动后端 (Spring Boot :8080, kafka profile) ..."; \
	(cd backend && KAFKA_BOOTSTRAP_SERVERS=localhost:9094 SPRING_KAFKA_CONSUMER_GROUP_ID=spring-order-dev mvn -o compile spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --spring.profiles.active=kafka,dev") & BACKEND=$$!; \
	trap 'echo; echo ">> 停止前后端..."; [ -n "$$BACKEND" ] && kill -- -$$BACKEND 2>/dev/null; [ -n "$$FRONTEND" ] && kill -- -$$FRONTEND 2>/dev/null' INT TERM EXIT; \
	echo ">> 等待后端就绪 (8080，最多 90s) ..."; \
	n=0; until curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; do \
		sleep 1; n=$$((n+1)); \
		if [ $$n -ge 90 ]; then echo "❌ 后端 90s 未就绪，请检查上方后端日志"; exit 1; fi; \
	done; \
	echo ">> 后端已就绪，启动前端 (Vite :3000) ..."; \
	(cd frontend && npx vite --port 3000) & FRONTEND=$$!; \
	wait

# ---- Dev: 全栈 (docker compose) —— P1 Redis + P3 Kafka + P4 多实例/nginx/grafana ----
# 与轻量 dev(单 H2 实例) 二选一，不要同时跑（端口 9094/3000 冲突）。
# 先宿主机预构建产物（后端 jar / 前端 dist），再 docker compose --build 起全栈：
#   2 副本(order-svc-1/2 :8081/:8082) -> nginx 网关(:8090) -> 前端(:18083)，
#   外加 Redis/Postgres/Kafka/Prometheus(:9090)/Grafana(:3000, admin/admin)。
# 启动后 P1(分布式锁+缓存) 与 P4(多实例+可观测) 才会真正激活。
dev-full:
	@echo ">> 预检: Docker daemon 是否就绪 ..."; if ! docker info >/dev/null 2>&1; then \
		echo "  ❌ 连不上 Docker daemon (socket /Users/erishen/.orbstack/run/docker.sock 不存在)"; \
		echo "     请先启动 OrbStack（启动台/应用程序 点开 OrbStack，或终端执行 open -a OrbStack），"; \
		echo "     待其运行状态变为「运行中」(菜单栏图标变色 / 终端 docker info 正常) 后重试。"; \
		echo "     若你用的是 Docker Desktop 而非 OrbStack，启动它即可，socket 路径会自动生效。"; \
		exit 1; \
	fi
	@echo ">> 清理端口 9094/3000(若被轻量 make dev 占用)..."; for p in 9094 3000; do PIDS=$$(lsof -ti:$$p); if [ -n "$$PIDS" ]; then echo "  释放 :$$p ($$PIDS)"; kill $$PIDS 2>/dev/null; fi; done; pkill -f "spring-boot:run" 2>/dev/null || true; sleep 2
	@echo ">> [1/3] 构建后端 jar (mvn -o package -DskipTests) ..."; cd backend && mvn -o package -DskipTests
	@echo ">> [2/3] 构建前端 dist (npm run build) ..."; cd frontend && npm run build
	@echo ">> [3/3] 启动全栈 (docker compose up -d --build) ..."; docker compose up -d --build
	@echo ">> 等待两个副本健康 (最多 150s) ..."; \
	ok=0; \
	for i in $$(seq 1 150); do \
		s1=$$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health 2>/dev/null); \
		s2=$$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/actuator/health 2>/dev/null); \
		if [ "$$s1" = "200" ] && [ "$$s2" = "200" ]; then echo "  ✅ 两副本已就绪 ($${i}s)"; ok=1; break; fi; \
		sleep 1; \
	done; \
	if [ "$$ok" != "1" ]; then \
		echo "  ❌ 副本 150s 内未就绪，下单必 502。常见原因: Postgres 迁移失败 / Redis·Kafka 未健康 / 端口冲突。"; \
		echo "  ---- order-svc-1 最近日志 ----"; docker compose logs --tail 50 order-svc-1 2>&1 | tail -50; \
		echo "  ---- order-svc-2 最近日志 ----"; docker compose logs --tail 50 order-svc-2 2>&1 | tail -50; \
		echo "  请贴出上面日志，或用 make dev-full-logs 看完整输出。"; \
		exit 1; \
	fi
	@echo "全栈已启动（副本已健康）。演示入口："
	@echo "  ★ 前端 UI     : http://localhost:18083   (经网关轮询两副本，请用这个地址)"
	@echo "  网关(直接)   : http://localhost:8090/api/orders"
	@echo "  Prometheus   : http://localhost:9090   (targets: order-svc-1/2)"
	@echo "  Grafana      : http://localhost:3000   (admin/admin，已置备数据源+看板)"
	@echo "  副本直连     : :8081 (svc-1) / :8082 (svc-2)"
	@echo "  停止: make dev-full-down | 日志: make dev-full-logs"
	@echo "  说明: 已自动释放 9094/3000 并停掉轻量 dev，避免 Kafka/端口冲突导致副本起不来"

dev-full-down:
	docker compose down

dev-full-logs:
	docker compose logs -f
