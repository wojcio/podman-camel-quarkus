package org.example.camel;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Camel Route for REST Bookshop API using Netty HTTP and SQLite.
 * 
 * This route provides a complete CRUD REST API for managing books:
 * - GET    /api/books          - List all books
 * - GET    /api/books/{id}     - Get book by ID
 * - POST   /api/books          - Create new book
 * - PUT    /api/books/{id}     - Update existing book
 * - DELETE /api/books/{id}     - Delete book by ID
 */
@ApplicationScoped
public class BookshopRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(BookshopRoute.class);
    
    @Inject
    ObjectMapper objectMapper;

    private static final String DB_URL = "jdbc:sqlite:deploy/bookshop.db";
    
    /**
     * Database initialization on startup.
     */
    @Override
    public void configure() throws Exception {
        // Initialize database on startup
        initDatabase();
        
        LOG.info("Starting Bookshop REST API Route on port 8081");

        // Main REST API route using Netty HTTP
        from("netty-http://0.0.0.0:8081?httpChannelPoolSize=20&allowNullValue=true")
            .routeId("bookshop-api-route")
            .log("Received request: ${header.CamelHttpMethod} ${header.CamelHttpPath}")
            
            // Route based on path and method
            .choice()
                // GET /api/books - List all books
                .when(simple("${header.CamelHttpMethod} == 'GET' && ${header.CamelHttpPath} == '/api/books'"))
                    .log("Listing all books")
                    .process(this::listAllBooks)
                    .setHeader("CamelHttpResponseCode", constant(200))
                    .setHeader("Content-Type", constant("application/json"))
                
                // GET /api/books/{id} - Get book by ID
                .when(simple("${header.CamelHttpPath} matches '/api/books/\\\\d+'"))
                    .log("Getting book by ID")
                    .process(this::getBookById)
                    .setHeader("CamelHttpResponseCode", constant(200))
                    .setHeader("Content-Type", constant("application/json"))
                
                // POST /api/books - Create new book
                .when(simple("${header.CamelHttpMethod} == 'POST' && ${header.CamelHttpPath} == '/api/books'"))
                    .log("Creating new book")
                    .process(this::createBook)
                    .setHeader("CamelHttpResponseCode", constant(201))
                    .setHeader("Content-Type", constant("application/json"))
                
                // PUT /api/books/{id} - Update existing book
                .when(simple("${header.CamelHttpMethod} == 'PUT' && ${header.CamelHttpPath} matches '/api/books/\\\\d+'"))
                    .log("Updating book by ID")
                    .process(this::updateBook)
                    .setHeader("CamelHttpResponseCode", constant(200))
                    .setHeader("Content-Type", constant("application/json"))
                
                // DELETE /api/books/{id} - Delete book
                .when(simple("${header.CamelHttpMethod} == 'DELETE' && ${header.CamelHttpPath} matches '/api/books/\\\\d+'"))
                    .log("Deleting book by ID")
                    .process(this::deleteBook)
                    .setHeader("CamelHttpResponseCode", constant(200))
                    .setHeader("Content-Type", constant("application/json"))
                
                // Health check endpoints
                .when(simple("${header.CamelHttpPath} == '/health/live' || ${header.CamelHttpPath} == '/health/ready'"))
                    .log("Health check requested")
                    .setBody(constant("{\"status\":\"UP\",\"checks\":[{\"name\":\"bookshop-api\",\"status\":\"UP\"}]}"))
                    .setHeader("CamelHttpResponseCode", constant(200))
                    .setHeader("Content-Type", constant("application/json"))
                
                // Root endpoint - API info
                .when(simple("${header.CamelHttpPath} == '/'"))
                    .log("Root endpoint requested")
                    .setBody(constant("""
                        {
                          "name": "Camel Quarkus Bookshop API",
                          "version": "1.0.0",
                          "endpoints": {
                            "GET /api/books": "List all books",
                            "GET /api/books/{id}": "Get book by ID",
                            "POST /api/books": "Create new book",
                            "PUT /api/books/{id}": "Update existing book",
                            "DELETE /api/books/{id}": "Delete book"
                          }
                        }
                        """))
                    .setHeader("CamelHttpResponseCode", constant(200))
                    .setHeader("Content-Type", constant("application/json"))
                
                // 404 for unknown routes
                .otherwise()
                    .log("Endpoint not found: ${header.CamelHttpPath}")
                    .setBody(simple("{\"error\": \"Endpoint not found: ${header.CamelHttpPath}\"}"))
                    .setHeader("CamelHttpResponseCode", constant(404))
                    .setHeader("Content-Type", constant("application/json"));
    }

    /**
     * Initialize the SQLite database and create tables.
     */
    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            LOG.info("Connecting to SQLite database: {}", DB_URL);
            
            // Create books table
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS books (
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
                """;
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSQL);
                LOG.info("Books table created/verified");
            }
            
            // Check if we need to seed sample data
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM books")) {
                
                if (rs.next() && rs.getInt(1) == 0) {
                    LOG.info("Seeding database with sample books...");
                    seedSampleData(conn);
                } else {
                    LOG.info("Database already contains data");
                }
            }
            
        } catch (SQLException e) {
            LOG.error("Failed to initialize database", e);
        }
    }

    /**
     * Seed the database with sample book data.
     */
    private void seedSampleData(Connection conn) throws SQLException {
        List<Map<String, Object>> books = new ArrayList<>();
        
        // Sample book data
        books.add(Map.of("title", "Modern Java in Action", "author", "Raoul-Gabriel Urma", 
                        "isbn", "978-1788399672", "price", 45.99, "stock", 25, "genre", "Programming"));
        books.add(Map.of("title", "Apache Camel in Action", "author", "Christian Posta", 
                        "isbn", "978-1617293407", "price", 39.99, "stock", 15, "genre", "Integration"));
        books.add(Map.of("title", "Quarkus in Action", "author", "Ken Finnigan", 
                        "isbn", "978-1617297436", "price", 54.99, "stock", 30, "genre", "Cloud Native"));
        books.add(Map.of("title", "Kubernetes Up and Running", "author", "Kelsey Hightower", 
                        "isbn", "978-1492046530", "price", 49.99, "stock", 20, "genre", "DevOps"));
        books.add(Map.of("title", "Building Microservices", "author", "Sam Newman", 
                        "isbn", "978-1491950357", "price", 52.99, "stock", 18, "genre", "Architecture"));
        
        String insertSQL = """
            INSERT INTO books (title, author, isbn, price, stock, genre) 
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        
        try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            for (Map<String, Object> book : books) {
                pstmt.setString(1, (String) book.get("title"));
                pstmt.setString(2, (String) book.get("author"));
                pstmt.setString(3, (String) book.get("isbn"));
                pstmt.setDouble(4, (Double) book.get("price"));
                pstmt.setInt(5, (Integer) book.get("stock"));
                pstmt.setString(6, (String) book.get("genre"));
                pstmt.executeUpdate();
                LOG.info("Seeded: {}", book.get("title"));
            }
        }
        
        LOG.info("Sample data seeded successfully!");
    }

    /**
     * List all books from the database.
     */
    private void listAllBooks(Exchange exchange) throws Exception {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM books ORDER BY id")) {
            
            List<Map<String, Object>> books = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> book = new java.util.HashMap<>();
                book.put("id", rs.getInt("id"));
                book.put("title", rs.getString("title"));
                book.put("author", rs.getString("author"));
                book.put("isbn", rs.getString("isbn"));
                book.put("price", rs.getBigDecimal("price"));
                book.put("stock", rs.getInt("stock"));
                book.put("genre", rs.getString("genre"));
                book.put("createdAt", rs.getTimestamp("created_at"));
                book.put("updatedAt", rs.getTimestamp("updated_at"));
                books.add(book);
            }
            
            String json = objectMapper.writeValueAsString(books);
            exchange.getIn().setBody(json);
        } catch (SQLException e) {
            LOG.error("Error listing books", e);
            throw new RuntimeException("Failed to list books", e);
        }
    }

    /**
     * Get a book by ID.
     */
    private void getBookById(Exchange exchange) throws Exception {
        String path = exchange.getIn().getHeader("CamelHttpPath", String.class);
        int bookId = Integer.parseInt(path.substring(12)); // Extract ID from /api/books/{id}
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT * FROM books WHERE id = ?")) {
            
            pstmt.setInt(1, bookId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> book = new java.util.HashMap<>();
                book.put("id", rs.getInt("id"));
                book.put("title", rs.getString("title"));
                book.put("author", rs.getString("author"));
                book.put("isbn", rs.getString("isbn"));
                book.put("price", rs.getBigDecimal("price"));
                book.put("stock", rs.getInt("stock"));
                book.put("genre", rs.getString("genre"));
                book.put("createdAt", rs.getTimestamp("created_at"));
                book.put("updatedAt", rs.getTimestamp("updated_at"));
                
                String json = objectMapper.writeValueAsString(book);
                exchange.getIn().setBody(json);
            } else {
                exchange.getIn().setBody("{\"error\": \"Book not found with id: " + bookId + "\"}");
                exchange.getIn().setHeader("CamelHttpResponseCode", 404);
            }
        } catch (SQLException e) {
            LOG.error("Error getting book by ID: {}", bookId, e);
            throw new RuntimeException("Failed to get book", e);
        }
    }

    /**
     * Create a new book.
     */
    private void createBook(Exchange exchange) throws Exception {
        String jsonBody = exchange.getIn().getBody(String.class);
        
        // Parse JSON to extract book data
        @SuppressWarnings("unchecked")
        Map<String, Object> bookData = objectMapper.readValue(jsonBody, Map.class);
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                 "INSERT INTO books (title, author, isbn, price, stock, genre) VALUES (?, ?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, (String) bookData.get("title"));
            pstmt.setString(2, (String) bookData.get("author"));
            pstmt.setString(3, (String) bookData.get("isbn"));
            pstmt.setDouble(4, Double.parseDouble(bookData.get("price").toString()));
            pstmt.setInt(5, Integer.parseInt(bookData.getOrDefault("stock", "0").toString()));
            pstmt.setString(6, (String) bookData.get("genre"));
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    int newId = rs.getInt(1);
                    
                    // Return the created book
                    Map<String, Object> createdBook = new java.util.HashMap<>();
                    createdBook.put("id", newId);
                    createdBook.put("title", bookData.get("title"));
                    createdBook.put("author", bookData.get("author"));
                    createdBook.put("isbn", bookData.get("isbn"));
                    createdBook.put("price", bookData.get("price"));
                    createdBook.put("stock", bookData.getOrDefault("stock", "0"));
                    createdBook.put("genre", bookData.get("genre"));
                    
                    String json = objectMapper.writeValueAsString(createdBook);
                    exchange.getIn().setBody(json);
                }
            } else {
                exchange.getIn().setBody("{\"error\": \"Failed to create book\"}");
                exchange.getIn().setHeader("CamelHttpResponseCode", 500);
            }
        } catch (SQLException e) {
            LOG.error("Error creating book", e);
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                exchange.getIn().setBody("{\"error\": \"Book with this ISBN already exists\"}");
                exchange.getIn().setHeader("CamelHttpResponseCode", 409);
            } else {
                throw new RuntimeException("Failed to create book", e);
            }
        }
    }

    /**
     * Update an existing book.
     */
    private void updateBook(Exchange exchange) throws Exception {
        String path = exchange.getIn().getHeader("CamelHttpPath", String.class);
        int bookId = Integer.parseInt(path.substring(12)); // Extract ID from /api/books/{id}
        
        String jsonBody = exchange.getIn().getBody(String.class);
        
        // Parse JSON to extract book data
        @SuppressWarnings("unchecked")
        Map<String, Object> bookData = objectMapper.readValue(jsonBody, Map.class);
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                 "UPDATE books SET title = ?, author = ?, isbn = ?, price = ?, stock = ?, genre = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            
            pstmt.setString(1, (String) bookData.get("title"));
            pstmt.setString(2, (String) bookData.get("author"));
            pstmt.setString(3, (String) bookData.get("isbn"));
            pstmt.setDouble(4, Double.parseDouble(bookData.get("price").toString()));
            pstmt.setInt(5, Integer.parseInt(bookData.getOrDefault("stock", "0").toString()));
            pstmt.setString(6, (String) bookData.get("genre"));
            pstmt.setInt(7, bookId);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // Return the updated book
                ResultSet rs = pstmt.getGeneratedKeys();
                
                // Fetch the updated book
                try (PreparedStatement selectStmt = conn.prepareStatement(
                     "SELECT * FROM books WHERE id = ?")) {
                    selectStmt.setInt(1, bookId);
                    ResultSet selectRs = selectStmt.executeQuery();
                    
                    if (selectRs.next()) {
                        Map<String, Object> updatedBook = new java.util.HashMap<>();
                        updatedBook.put("id", selectRs.getInt("id"));
                        updatedBook.put("title", selectRs.getString("title"));
                        updatedBook.put("author", selectRs.getString("author"));
                        updatedBook.put("isbn", selectRs.getString("isbn"));
                        updatedBook.put("price", selectRs.getBigDecimal("price"));
                        updatedBook.put("stock", selectRs.getInt("stock"));
                        updatedBook.put("genre", selectRs.getString("genre"));
                        updatedBook.put("updatedAt", selectRs.getTimestamp("updated_at"));
                        
                        String json = objectMapper.writeValueAsString(updatedBook);
                        exchange.getIn().setBody(json);
                    }
                }
            } else {
                exchange.getIn().setBody("{\"error\": \"Book not found with id: " + bookId + "\"}");
                exchange.getIn().setHeader("CamelHttpResponseCode", 404);
            }
        } catch (SQLException e) {
            LOG.error("Error updating book by ID: {}", bookId, e);
            throw new RuntimeException("Failed to update book", e);
        }
    }

    /**
     * Delete a book by ID.
     */
    private void deleteBook(Exchange exchange) throws Exception {
        String path = exchange.getIn().getHeader("CamelHttpPath", String.class);
        int bookId = Integer.parseInt(path.substring(12)); // Extract ID from /api/books/{id}
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM books WHERE id = ?")) {
            
            pstmt.setInt(1, bookId);
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                exchange.getIn().setBody("{\"message\": \"Book deleted successfully\", \"id\": " + bookId + "}");
            } else {
                exchange.getIn().setBody("{\"error\": \"Book not found with id: " + bookId + "\"}");
                exchange.getIn().setHeader("CamelHttpResponseCode", 404);
            }
        } catch (SQLException e) {
            LOG.error("Error deleting book by ID: {}", bookId, e);
            throw new RuntimeException("Failed to delete book", e);
        }
    }
}