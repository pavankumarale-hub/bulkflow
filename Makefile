.PHONY: all build test up down demo clean logs health metrics deadletter

all: build

build:
	./mvnw clean package -DskipTests

test:
	./mvnw test

up:
	docker compose up -d postgres minio minio-init
	@echo "Waiting for services to be ready..."
	@sleep 5
	@echo "Services ready. Run 'make demo' to drop sample data and run a batch."

down:
	docker compose down -v

demo: up
	@echo "Generating sample data and launching demo batch..."
	./scripts/demo.sh

logs:
	docker compose logs -f app

clean:
	./mvnw clean
	docker compose down -v --remove-orphans

health:
	@curl -s http://localhost:8080/actuator/health | python3 -m json.tool

metrics:
	@curl -s http://localhost:8080/api/metrics/summary | python3 -m json.tool

deadletter:
	@curl -s "http://localhost:8080/api/dead-letter?page=0&size=20" | python3 -m json.tool

lint:
	./mvnw checkstyle:check

mvnw-download:
	mvn wrapper:wrapper -Dmaven=3.9.6
