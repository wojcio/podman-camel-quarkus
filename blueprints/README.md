# Camel Blueprints

This directory contains example Camel route definitions in different formats:

## Available Blueprints

| File | Format | Description |
|------|--------|-------------|
| `camel-route.xml` | XML | XML-based Camel routes using Spring XML namespace |
| `camel-route.yaml` | YAML | YAML-based Camel routes for simple routing scenarios |

## How to Use These Blueprints

### Option 1: Copy to Source Directory (Recommended for Development)

1. **Copy the blueprint file** to `src/main/resources/camel/` directory:
   ```bash
   # For XML blueprint
   cp blueprints/camel-route.xml src/main/resources/camel/
   
   # For YAML blueprint
   cp blueprints/camel-route.yaml src/main/resources/camel/
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

## Configuration Properties

All blueprints use the following environment variables:

| Property | Default | Description |
|----------|---------|-------------|
| `camel.input.dir` | `/home/quarkus/deploy/input` | Input directory for files to process |
| `camel.output.dir` | `/home/quarkus/deploy/output` | Output directory for processed files |
| `camel.archive.dir` | `/home/quarkus/deploy/archive` | Archive directory for original files |

## Example Flow

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

## See Also

- [Camel Quarkus Documentation](https://camel.apache.org/camel-quarkus/latest/)
- [XML Routes](https://camel.apache.org/camel-quarkus/latest/alternatives/xml.html)
- [YAML Routes](https://camel.apache.org/camel-quarkus/latest/alternatives/yaml.html)