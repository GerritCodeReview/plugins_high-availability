.PHONY: help build up-active-active clean logs open

help: ## Display this help message.
	@awk 'BEGIN {FS = ":.*?## "}; /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

.DEFAULT_GOAL := help

# Determine whether to use "docker-compose" or "docker compose"
DOCKER_COMPOSE := $(shell which docker-compose 2>/dev/null)
ifeq ($(DOCKER_COMPOSE),)
	DOCKER_COMPOSE := $(shell which docker 2>/dev/null)
	DOCKER_COMPOSE_CMD := compose
else
	DOCKER_COMPOSE_CMD := up -d
endif

build: ## Build the containers with no cache
	$(DOCKER_COMPOSE) build --no-cache

up-active-active: ## Start containers in detached mode with active/active
	export HA_MODE=active-active; $(DOCKER_COMPOSE) $(DOCKER_COMPOSE_CMD)

clean: ## Stop and remove all containers
	$(DOCKER_COMPOSE) down

logs: ## View the logs from the containers
	$(DOCKER_COMPOSE) logs -f

open: ## Open a web page in port 8080 (HA proxy)
	open http://localhost:8080/
