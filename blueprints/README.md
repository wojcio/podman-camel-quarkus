# Camel Blueprints

This directory contains example Camel route definitions in different formats:

## Available Blueprints

| File | Format | Description |
|------|--------|-------------|
| `camel-route.xml` | XML | XML-based Camel routes using Spring XML namespace |
| `camel-route.yaml` | YAML | YAML-based Camel routes for simple routing scenarios |
| `bookshop-route.xml` | XML | **REST Bookshop API** with Netty HTTP and SQLite database |

## Bookshop REST API (New!) ⭐

The `bookshop-route.xml` blueprint provides a complete RESTful Bookshop API using:
- **Netty HTTP** component for REST endpoints (runs on port 8081)
- **JDBC** component with SQLite database for data persistence
- **Jackson** for JSON serialization/deserialization

### REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/books` | List all books |
| GET | `/api/books/{id}` | Get book by ID |
| POST | `/api/books` | Create new book |
| PUT | `/api/books/{id}` | Update existing book |
| DELETE | `/api/books/{id}` | Delete book by ID |

### Database Schema

The SQLite database (`deploy/bookshop.db`) contains a `books` table:

```sql
CREATE TABLE books (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    isbn VARCHAR(20) UNIQUE,
    price DECIMAL(10,2) NOT NULL,
    stock INTEGER DEFAULT 0,
    genre VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

### Sample Data

On first run, the database is seeded with 5 sample books:
1. Modern Java in Action
2. Apache Camel in Action
3. Quarkus in Action
4. Kubernetes Up and Running
5. Building Microservices

### Example Usage

**List all books:**
```bash
curl http://localhost:8081/api/books
```

**Get a specific book:**
```bash
curl http://localhost:8081/api/books/1
```

**Create a new book:**
```bash
curl -X POST http://localhost:8081/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "title": "New Book",
    "author": "Author Name",
    "isbn": "123-4567890123",
    "price": 29.99,
    "stock": 10,
    "genre": "Fiction"
  }'
```

**Update a book:**
```bash
curl -X PUT http://localhost:8081/api/books/1 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Updated Title",
    "author": "Author Name",
    "isbn": "978-1788399672",
    "price": 39.99,
    "stock": 20,
    "genre": "Programming"
  }'
```

**Delete a book:**
```bash
curl -X DELETE http://localhost:8081/api/books/1
```

## How to Use Blueprints

### Option 1: Copy to Source Directory (Recommended for Development)

1. **Copy the blueprint file** to `src/main/resources/camel/` directory:
   ```bash
   # For XML blueprint
   cp blueprints/camel-route.xml src/main/resources/camel/
   
   # For YAML blueprint
   cp blueprints/camel-route.yaml src/main/resources/camel/
   
   # For Bookshop API (Java implementation is auto-loaded)
   # The bookshop-route.xml is for reference - use BookshopRoute.java
   ```

2. **Rebuild the application**:
   ```bash
   mvn clean package
   ```

3. **Rebuild the container**:
   ```bash
   podman build -t quay.io/wojcio/camel-quarkus:latest .
   ```

4. **Restart the container**:
   ```bash
   ./run.sh
   # or manually stop and restart
   podman stop podman-camel-quarkus
   podman rm podman-camel-quarkus
   podman run -d \
     --name podman-camel-quarkus \
     -p 8080:8080 \
     -p 8081:8081 \
     -v $(pwd)/deploy:/home/quarkus/deploy:Z \
     --network camel-network \
     -e PORT=8080 \
     -e CAMEL_INPUT_DIR=/home/quarkus/deploy/input \
     -e CAMEL_OUTPUT_DIR=/home/quarkus/deploy/output \
     -e CAMEL_ARCHIVE_DIR=/home/quarkus/deploy/archive \
     quay.io/wojcio/camel-quarkus:latest
   ```

### Option 2: Hot Load to Running Container

For XML blueprints, you can use Camel's file watcher feature:

1. **Create a watch directory in the container**:
   ```bash
   podman exec podman-camel-quarkus mkdir -p /home/quarkus/blueprints
   ```

2. **Copy blueprint to the container**:
   ```bash
   podman cp blueprints/camel-route.xml podman-camel-quarkus:/home/quarkus/blueprints/
   ```

3. **Use Camel's file watcher to auto-discover routes** (requires additional configuration)

## Blueprint Comparison

### Java Routes (`CamelRoute.java`)
- **Pros**: Type-safe, IDE autocomplete, easy debugging
- **Cons**: Requires recompilation for changes

### XML Routes (`camel-route.xml`)
- **Pros**: Configuration-based, no recompilation needed for simple changes
- **Cons**: Less IDE support, more verbose

### YAML Routes (`camel-route.yaml`)
- **Pros**: Concise, human-readable
- **Cons**: Limited DSL support, less IDE support

### Bookshop API (`BookshopRoute.java`)
- **Pros**: Full REST API, database persistence, CRUD operations ready-to-use
- **Cons**: Requires Java recompilation for changes

## Configuration Properties

### File Processor Routes (camel-route.xml/yaml)

| Property | Default | Description |
|----------|---------|-------------|
| `camel.input.dir` | `/home/quarkus/deploy/input` | Input directory for files to process |
| `camel.output.dir` | `/home/quarkus/deploy/output` | Output directory for processed files |
| `camel.archive.dir` | `/home/quarkus/deploy/archive` | Archive directory for original files |

### Bookshop API Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `quarkus.http.port` | 8080 | Health endpoints port |
| `quarkus.netty.ports` | 8081 | Bookshop API Netty port |
| `quarkus.datasource.jdbc.url` | jdbc:sqlite:deploy/bookshop.db | SQLite database path |

## Example Flow - File Processor

When a file is placed in the input directory:

1. **Input**: `deploy/input/test.txt` with content "hello"
2. **Processing**: Content converted to uppercase
3. **Output**: `deploy/output/test.txt` with content "HELLO"
4. **Archive**: `deploy/archive/test.txt` (original)
5. **Processed**: `deploy/input/.camel/processed/test.txt`

## Troubleshooting

### Routes not loading
- Check that the blueprint is in `src/main/resources/camel/`
- Verify file extension: `.xml` or `.yaml`
- Check container logs: `podman logs podman-camel-quarkus`

### Property placeholders not resolved
- Ensure environment variables are set correctly in the container
- Use `${property.name}` syntax for placeholders

### Bookshop API not responding on port 8081
- Verify the container exposes port 8081: `podman port podman-camel-quarkus`
- Check if Netty is bound: `podman exec podman-camel-quarkus netstat -tlnp`
- Review logs: `podman logs podman-camel-quarkus | grep -i netty`

### SQLite database issues
- Ensure `deploy/` directory is writable
- Check if bookshop.db file exists: `ls -la deploy/bookshop.db`
- View database schema: `sqlite3 deploy/bookshop.db ".schema"`

## See Also

- [Camel Quarkus Documentation](https://camel.apache.org/camel-quarkus/latest/)
- [XML Routes](https://camel.apache.org/camel-quarkus/latest/alternatives/xml.html)
- [YAML Routes](https://camel.apache.org/camel-quarkus/latest/alternatives/yaml.html)
- [Netty HTTP Component](https://camel.apache.org/components/latest/netty-http-component.html)
- [JDBC Component](https://camel.apache.org/components/latest/jdbc-component.html)