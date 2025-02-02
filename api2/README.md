# Lumin API Server

## Prerequisites

- Java 17 (for Clojure)
- Clojure

## Deployment (Kubernetes)

#TODO

## Development & Testing (Podman/Docker)

1. Spin up a Postgres instance:

    ```sh
    $ podman run \
      --name postgres-test \
      -e POSTGRES_PASSWORD=Password123 \
      -e 5432:5432 \
      -d \
      docker.io/library/postgres:17
    ```
    
2. Run development mode (NOTE!! THIS WILL DROP ALL TABLES IN THE `PUBLIC`
   SCHEMA)
   
   ```sh
   $ cd api2
   $ clj -X:dev
   # Alternatively, use `clojure` CLI:
   $ clojure -X:dev
   ```
