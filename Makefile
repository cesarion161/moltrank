.PHONY: dev db-up db-down backend-test backend-verify frontend-test anchor-test smoke-endpoints smoke-clawgic clawgic-demo

dev:
	./scripts/dev-start.sh

db-up:
	docker compose up -d postgres

db-down:
	docker compose down

backend-test:
	cd backend && ./gradlew test

backend-verify:
	cd backend && ./gradlew check

frontend-test:
	cd frontend && npm test

anchor-test:
	cd anchor && anchor build && anchor test

smoke-endpoints:
	./scripts/smoke-endpoints.sh

smoke-clawgic:
	./scripts/smoke-clawgic.sh

clawgic-demo:
	./scripts/clawgic-demo-runner.sh
