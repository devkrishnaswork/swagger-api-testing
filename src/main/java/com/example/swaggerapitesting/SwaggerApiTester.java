package com.example.swaggerapitesting;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class SwaggerApiTester {
    private static final Logger logger = LoggerFactory.getLogger(SwaggerApiTester.class);
    private static final OkHttpClient client = new OkHttpClient();
    private static String baseUrl;

    public static void main(String[] args) {
        if (args.length != 2) {
            logger.error("Usage: java -jar swagger-api-tester.jar <swagger-file-path> <output-log-file>");
            return;
        }

        String swaggerFilePath = args[0];
        String outputLogFile = args[1];

        OpenAPI openAPI = parseSwagger(swaggerFilePath);

        if (openAPI != null) {
            List<Server> servers = openAPI.getServers();
            if (servers != null && !servers.isEmpty()) {
                baseUrl = servers.get(0).getUrl();
                logger.info("Base URL: {}", baseUrl);
            } else {
                logger.error("No servers defined in the Swagger file.");
                return;
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(outputLogFile, true))) {
                testApis(openAPI, writer);
            } catch (IOException e) {
                logger.error("Error writing to log file: {}", e.getMessage());
            }
        }
    }

    private static OpenAPI parseSwagger(String filePath) {
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(filePath, null, null);
        if (result.getMessages().isEmpty()) {
            logger.info("Swagger file is valid.");
            return result.getOpenAPI();
        } else {
            logger.error("Swagger file is invalid: {}", result.getMessages());
            return null;
        }
    }

    private static void testApis(OpenAPI openAPI, PrintWriter writer) {
        Paths paths = openAPI.getPaths();
        for (Map.Entry<String, PathItem> entry : paths.entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            if (pathItem.getGet() != null) {
                testApi(path, "GET", pathItem.getGet().getResponses(), writer);
            }
            // Add similar blocks for other HTTP methods (POST, PUT, DELETE, etc.)
        }
    }

    private static void testApi(String path, String method, ApiResponses expectedResponses, PrintWriter writer) {
        String url = baseUrl + path;
        Request request = new Request.Builder().url(url).build();

        long startTime = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            long endTime = System.currentTimeMillis();
            int statusCode = response.code();
            String responseBody = response.body().string();

            String expectedStatusCode = Integer.toString(statusCode);
            ApiResponse expectedResponse = expectedResponses.get(expectedStatusCode);
            if (expectedResponse != null) {
                MediaType mediaType = expectedResponse.getContent().values().iterator().next();
                Schema<?> schema = mediaType.getSchema();
                // You may need a library like `com.fasterxml.jackson` to compare JSON payloads properly
                // Here, we only log the schema and response body for demonstration purposes

                String logMessage;
                if (validateResponse(responseBody, schema)) {
                    logMessage = String.format("API [%s %s] passed with status %d in %d ms. Response: %s", method, path, statusCode, endTime - startTime, responseBody);
                } else {
                    logMessage = String.format("API [%s %s] failed in payload validation with status %d in %d ms. Expected Schema: %s, Response: %s", method, path, statusCode, endTime - startTime, schema, responseBody);
                }
                logger.info(logMessage);
                writer.println(logMessage);
            } else {
                String logMessage = String.format("API [%s %s] received unexpected status %d in %d ms. Response: %s", method, path, statusCode, endTime - startTime, responseBody);
                logger.error(logMessage);
                writer.println(logMessage);
            }
        } catch (IOException e) {
            String logMessage = String.format("API [%s %s] call failed: %s", method, path, e.getMessage());
            logger.error(logMessage);
            writer.println(logMessage);
        }
    }

    private static boolean validateResponse(String responseBody, Schema<?> schema) {
        // Implement validation logic here, for now returning true as a placeholder
        return true;
    }
}
