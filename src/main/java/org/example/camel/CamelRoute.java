package org.example.camel;

import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel Route for processing files from the deploy directory.
 * 
 * This route monitors a source directory and processes files by:
 * 1. Reading files from the input directory
 * 2. Logging the file content
 * 3. Moving processed files to an output directory
 * 4. Storing processed files in .camel subdirectory
 * 5. Deleting files from input directory after processing
 */
public class CamelRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(CamelRoute.class);

    @Override
    public void configure() throws Exception {
        // Directory paths - these will be linked to the deploy folder
        String inputDir = System.getProperty("camel.input.dir", "deploy/input");
        String outputDir = System.getProperty("camel.output.dir", "deploy/output");
        String archiveDir = System.getProperty("camel.archive.dir", "deploy/archive");
        String camelDir = System.getProperty("camel.processed.dir", ".camel");

        // Ensure directories exist
        new java.io.File(inputDir).mkdirs();
        new java.io.File(outputDir).mkdirs();
        new java.io.File(archiveDir).mkdirs();
        new java.io.File(camelDir).mkdirs();

        LOG.info("Starting Camel Route with directories:");
        LOG.info("  Input:     {}", inputDir);
        LOG.info("  Output:    {}", outputDir);
        LOG.info("  Archive:   {}", archiveDir);
        LOG.info("  Processed: {}", camelDir);

        // Directory for moved files after processing
        String processedInputDir = inputDir + "/.camel-moved";

        // Ensure moved files directory exists
        new java.io.File(processedInputDir).mkdirs();

        // Main route - watches for files in the input directory
        from("file:" + inputDir + "?move=" + processedInputDir + "&include=.*\\.txt")
            .routeId("file-processor-route")
            .log("Received file: ${header.CamelFileName}")
            .log("File content: ${body}")
            
            // Process the file content
            .process(exchange -> {
                String body = exchange.getIn().getBody(String.class);
                String upperBody = body.toUpperCase();
                exchange.getIn().setBody(upperBody);
                exchange.getIn().setHeader("processedAt", System.currentTimeMillis());
            })
            
            // Save processed file to output directory
            .to("file:" + outputDir + "?fileName=${header.CamelFileName}")
            
            // Archive the original file
            .to("file:" + archiveDir + "?fileName=${header.CamelFileName}&fileExist=Override")
            
            // Store processed file in .camel subdirectory
            .to("file:" + camelDir + "/processed?fileName=${header.CamelFileName}")
            
            .log("File processed and archived: ${header.CamelFileName}");

        // Health check endpoint
        from("direct:health")
            .routeId("health-check")
            .setBody(constant("{\"status\":\"UP\",\"checks\":[{\"name\":\"file-processor\",\"status\":\"UP\"}]}"));
    }
}