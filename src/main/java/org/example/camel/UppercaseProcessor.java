package org.example.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor to convert message body to uppercase.
 * Used by both Java and XML route definitions.
 */
public class UppercaseProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(UppercaseProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        String upperBody = body != null ? body.toUpperCase() : "";
        exchange.getIn().setBody(upperBody);
        exchange.getIn().setHeader("processedAt", System.currentTimeMillis());
        LOG.debug("Content converted to uppercase");
    }
}