.PHONY: install run test lint build

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

# ---- Q8: CI Pipeline Simulation ----
ci: backend-test backend-coverage-check
	@echo "✅ CI pipeline passed — coverage gate OK"

# ---- Quick Start ----
run: backend-run
