.PHONY: dev db-up db-down backend-test backend-verify frontend-test anchor-test smoke-endpoints

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
