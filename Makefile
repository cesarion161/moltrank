.PHONY: dev db-up db-down backend-test frontend-test anchor-test smoke-endpoints

dev:
	./scripts/dev-start.sh

db-up:
	docker compose up -d postgres

db-down:
	docker compose down

backend-test:
	cd backend && ./gradlew test

frontend-test:
	cd frontend && npm test

anchor-test:
	cd anchor && anchor build && anchor test

smoke-endpoints:
	./scripts/smoke-endpoints.sh
