# SignalMind — Developer convenience targets
# Requires: Docker Compose V2 (docker compose), GNU Make

.PHONY: help up down reset logs ps psql redis-cli build test clean

# ── Defaults ─────────────────────────────────────────────────────────────────
COMPOSE      := docker compose
GRADLE       := ./gradlew
PG_USER      ?= $(shell grep DB_USERNAME .env 2>/dev/null | cut -d= -f2 || echo signalmind)
PG_DB        ?= $(shell grep DB_NAME     .env 2>/dev/null | cut -d= -f2 || echo signalmind)
REDIS_PASS   ?= $(shell grep ^REDIS_PASSWORD .env 2>/dev/null | cut -d= -f2)

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*##' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*##"}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

# ── Infrastructure ────────────────────────────────────────────────────────────
up: ## Start PostgreSQL + Redis in the background
	$(COMPOSE) up -d
	@echo "Waiting for services to be healthy..."
	@$(COMPOSE) wait postgres redis || true
	@echo ""
	@$(MAKE) ps

down: ## Stop and remove containers (volumes preserved)
	$(COMPOSE) down

reset: ## Stop containers AND remove all volumes (wipes data!)
	@echo "⚠  This will DELETE all local database and Redis data."
	@read -p "Are you sure? [y/N] " ans && [ "$$ans" = "y" ]
	$(COMPOSE) down -v

ps: ## Show service status
	$(COMPOSE) ps

logs: ## Tail logs from all services (Ctrl-C to stop)
	$(COMPOSE) logs -f

logs-postgres: ## Tail PostgreSQL logs
	$(COMPOSE) logs -f postgres

logs-redis: ## Tail Redis logs
	$(COMPOSE) logs -f redis

# ── Database helpers ──────────────────────────────────────────────────────────
psql: ## Open psql shell inside the running PostgreSQL container
	$(COMPOSE) exec postgres psql -U $(PG_USER) -d $(PG_DB)

redis-cli: ## Open redis-cli inside the running Redis container
	@if [ -n "$(REDIS_PASS)" ]; then \
	  $(COMPOSE) exec redis redis-cli -a $(REDIS_PASS); \
	else \
	  $(COMPOSE) exec redis redis-cli; \
	fi

# ── Application ───────────────────────────────────────────────────────────────
build: ## Compile and package the application (skip tests)
	$(GRADLE) build -x test

build-full: ## Compile, test, and package the application
	$(GRADLE) build

test: ## Run unit tests
	$(GRADLE) test

run: up ## Start infrastructure then run the Spring Boot app locally
	$(GRADLE) bootRun

clean: ## Clean build artefacts
	$(GRADLE) clean
