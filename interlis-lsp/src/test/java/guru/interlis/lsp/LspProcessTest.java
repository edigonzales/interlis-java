package guru.interlis.lsp;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real stdio protocol test: stdout must contain framed LSP JSON only. */
class LspProcessTest {
    @Test
    @SuppressWarnings("try")
    void initializeShutdownAndExitOverStdio() throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(java.toString(), "-cp",
                System.getProperty("java.class.path"), "guru.interlis.lsp.Main")
                .redirectErrorStream(false)
                .start();
        try (OutputStream input = process.getOutputStream();
             InputStream output = new BufferedInputStream(process.getInputStream())) {
            send(input,"{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"workspace/executeCommand\",\"params\":{\"command\":\"interlis.compile\"}}");
            assertTrue(readResponse(output,99).contains("-32002"));
            send(input, """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                      "processId":null,"rootUri":null,"capabilities":{}
                    }}""");
            String initialize = readMessage(output);
            assertTrue(initialize.contains("\"id\":1"), initialize);
            assertTrue(initialize.contains("\"capabilities\""), initialize);
            assertTrue(initialize.contains("\"textDocumentSync\""), initialize);
            assertTrue(initialize.contains("interlis.diagram"), initialize);

            String uri = "memory:/lsp-process.ili";
            String model = """
                    INTERLIS 2.4;
                    MODEL LspProcess AT "https://example.interlis.ch" VERSION "1.0" =
                      TOPIC Data =
                        CLASS Building =
                          height : 0..999;
                        END Building;
                      END Data;
                    END LspProcess.
                    """;
            send(input, new Gson().toJson(Map.of(
                    "jsonrpc", "2.0",
                    "method", "textDocument/didOpen",
                    "params", Map.of("textDocument", Map.of(
                            "uri", uri, "languageId", "interlis", "version", 1,
                            "text", model)))));
            send(input, new Gson().toJson(Map.of(
                    "jsonrpc", "2.0", "id", 2, "method", "workspace/executeCommand",
                    "params", Map.of("command", "interlis.diagram",
                            "arguments", List.of(Map.of("uri", uri,
                                    "symbolIds", List.of()))))));
            String diagram = readResponse(output, 2);
            assertTrue(diagram.contains("snapshotId"), diagram);
            assertTrue(diagram.contains("Building"), diagram);

            send(input, new Gson().toJson(Map.of(
                    "jsonrpc", "2.0", "id", 3, "method", "workspace/executeCommand",
                    "params", Map.of("command", "interlis.diagram",
                            "arguments", List.of(Map.of("uri", uri,
                                    "snapshotId", "stale-snapshot",
                                    "symbolIds", List.of()))))));
            String stale = readResponse(output, 3);
            assertTrue(stale.contains("\"error\""), stale);
            assertTrue(stale.contains("stale"), stale);

            send(input, new Gson().toJson(Map.of(
                    "jsonrpc", "2.0", "id", 4, "method", "workspace/executeCommand",
                    "params", Map.of("command", "interlis.compile", "arguments", List.of()))));
            String compiled = readResponse(output, 4);
            assertTrue(compiled.contains("succeeded"), compiled);

            var document=Map.of("uri",uri);
            var cursor=Map.of("line",4,"character",8);
            var features=List.of(
                    Map.of("method","textDocument/hover","params",Map.of("textDocument",document,"position",cursor)),
                    Map.of("method","textDocument/definition","params",Map.of("textDocument",document,"position",cursor)),
                    Map.of("method","textDocument/references","params",Map.of("textDocument",document,"position",cursor,"context",Map.of("includeDeclaration",true))),
                    Map.of("method","textDocument/rename","params",Map.of("textDocument",document,"position",cursor,"newName","renamedHeight")),
                    Map.of("method","textDocument/completion","params",Map.of("textDocument",document,"position",cursor)),
                    Map.of("method","textDocument/formatting","params",Map.of("textDocument",document,"options",Map.of("tabSize",4,"insertSpaces",true))),
                    Map.of("method","textDocument/semanticTokens/full","params",Map.of("textDocument",document)),
                    Map.of("method","textDocument/foldingRange","params",Map.of("textDocument",document)));
            int featureId=10;
            for(var feature:features) {
                send(input,new Gson().toJson(Map.of("jsonrpc","2.0","id",featureId,"method",feature.get("method"),"params",feature.get("params"))));
                String answer=readResponse(output,featureId++);
                assertFalse(answer.contains("\"error\""),answer); assertFalse(answer.contains("\"result\":null"),answer);
                if(feature.get("method").equals("textDocument/rename")) assertTrue(answer.contains("renamedHeight"),answer);
                if(feature.get("method").equals("textDocument/completion")) assertTrue(answer.contains("replacementRange") || answer.contains("textEdit"),answer);
            }

            send(input, "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"shutdown\",\"params\":null}");
            String shutdown = readResponse(output, 5);
            assertTrue(shutdown.contains("\"id\":5"), shutdown);
            send(input,"{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"workspace/executeCommand\",\"params\":{\"command\":\"interlis.compile\"}}");
            assertTrue(readResponse(output,6).contains("-32600"));

            send(input, "{\"jsonrpc\":\"2.0\",\"method\":\"exit\",\"params\":null}");
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "LSP process did not exit");
            assertEquals(0, process.exitValue());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    @Test void exitWithoutShutdownTerminatesWithFailureWhileInputRemainsOpen() throws Exception {
        var java=Path.of(System.getProperty("java.home"),"bin","java");
        var process=new ProcessBuilder(java.toString(),"-cp",System.getProperty("java.class.path"),"guru.interlis.lsp.Main").start();
        try {
            send(process.getOutputStream(),"{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}");
            assertTrue(process.waitFor(5,TimeUnit.SECONDS)); assertEquals(1,process.exitValue());
        } finally { process.destroyForcibly(); }
    }

    private static String readResponse(InputStream input, int id) throws IOException {
        while (true) {
            String message = readMessage(input);
            if (message.contains("\"id\":" + id)) {
                return message;
            }
        }
    }

    private static void send(OutputStream output, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        output.write(("Content-Length: " + bytes.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        output.write(bytes);
        output.flush();
    }

    private static String readMessage(InputStream input) throws IOException {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int value = input.read();
            if (value < 0) {
                throw new IOException("unexpected EOF while reading LSP headers");
            }
            headers.write(value);
            state = switch (state) {
                case 0 -> value == '\r' ? 1 : 0;
                case 1 -> value == '\n' ? 2 : 0;
                case 2 -> value == '\r' ? 3 : 0;
                case 3 -> value == '\n' ? 4 : 0;
                default -> state;
            };
        }
        String headerText = headers.toString(StandardCharsets.US_ASCII);
        int length = -1;
        for (String line : headerText.split("\r\n")) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                length = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        assertTrue(length >= 0, headerText);
        byte[] body = input.readNBytes(length);
        assertEquals(length, body.length);
        String message = new String(body, StandardCharsets.UTF_8);
        assertFalse(message.isBlank());
        return message;
    }
}
