package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeSmokeTest {

    private ScriptRunnerService newSvc() {
        return new ScriptRunnerService(new CommandResolver(),
                List.of(new PythonRunner(),
                        new NodeRunner(new CommandResolver()),
                        new JavaRunner(new CommandResolver())));
    }

    @Test
    void nodeReadsArgvAndEnv() throws Exception {
        Path script = Files.createTempFile("smoke", ".js");
        Files.writeString(script,
                "const fs=require(\"fs\");" +
                "const dir=process.argv[2];" +
                "const names=process.argv.slice(3);" +
                "console.log(\"ARGVDIR=\"+dir);" +
                "console.log(\"NAME=\"+(names.length?fs.readFileSync(names[0],\"utf8\"):\"none\"));" +
                "console.log(\"ENVDIR=\"+(process.env.DATA_DIR||\"\"));");
        Map<String, byte[]> data = new LinkedHashMap<>();
        data.put("hello.txt", "hi-node".getBytes());
        ScriptRunnerService.ScriptRunResult r = newSvc().runBy("node", script, data);
        if (r.isPythonFound()) {
            assertEquals(0, r.getExitCode());
            assertTrue(r.getOutput().contains("ARGVDIR="));
            assertTrue(r.getOutput().contains("NAME=hi-node"));
            assertTrue(r.getOutput().contains("ENVDIR="));
        }
        Files.deleteIfExists(script);
    }

    @Test
    void javaReadsEnv() throws Exception {
        CommandResolver cr = new CommandResolver();
        if (!cr.isJavaPresent()) {
            return;
        }
        String javaHome = System.getenv("JAVA_HOME");
        String javacPath = javaHome != null && !javaHome.isEmpty()
                ? javaHome + "\\bin\\javac"
                : "javac";
        Path tmpDir = Files.createTempDirectory("smoke_java");
        try {
            Path src = tmpDir.resolve("Smoke.java");
            Files.writeString(src,
                    "public class Smoke {" + System.lineSeparator() +
                    "  public static void main(String[] a) {" + System.lineSeparator() +
                    "    System.out.println(\"DATA_DIR=\" + System.getenv(\"DATA_DIR\"));" + System.lineSeparator() +
                    "    System.out.println(\"INPUT_FILES=\" + System.getenv(\"INPUT_FILES\"));" + System.lineSeparator() +
                    "  }" + System.lineSeparator() +
                    "}" + System.lineSeparator());
            ProcessBuilder compilePb = new ProcessBuilder(javacPath, src.toAbsolutePath().toString());
            compilePb.directory(tmpDir.toFile());
            compilePb.redirectErrorStream(true);
            Process compileProc = compilePb.start();
            String compileOut = new String(compileProc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            compileProc.waitFor();
            if (compileProc.exitValue() != 0) {
                return;
            }
            Path manifest = tmpDir.resolve("MANIFEST.MF");
            Files.writeString(manifest,
                    "Manifest-Version: 1.0" + System.lineSeparator() +
                    "Main-Class: Smoke" + System.lineSeparator());
            Path jarFile = tmpDir.resolve("smoke.jar");
            ProcessBuilder jarPb = new ProcessBuilder(
                    "jar", "cfm", jarFile.toAbsolutePath().toString(),
                    manifest.toAbsolutePath().toString(), "-C",
                    tmpDir.toAbsolutePath().toString(), "Smoke.class");
            jarPb.directory(tmpDir.toFile());
            jarPb.redirectErrorStream(true);
            Process jarProc = jarPb.start();
            String jarOut = new String(jarProc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            jarProc.waitFor();
            if (jarProc.exitValue() != 0) {
                return;
            }
            Map<String, byte[]> data = new LinkedHashMap<>();
            data.put("a.txt", "hello".getBytes());
            ScriptRunnerService.ScriptRunResult r = newSvc().runBy("java", jarFile, data);
            assertEquals(0, r.getExitCode());
            assertTrue(r.getOutput().contains("DATA_DIR="));
            assertTrue(r.getOutput().contains("INPUT_FILES="));
        } finally {
            deleteRecursive(tmpDir);
        }
    }

    private static void deleteRecursive(Path path) throws Exception {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : (Iterable<Path>) entries::iterator) {
                    deleteRecursive(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    @Test
    void pythonLegacyStillWorks() throws Exception {
        Path script = Files.createTempFile("smoke", ".py");
        Files.writeString(script,
                "import sys,os\n" +
                "print('ARGV='+sys.argv[1])\n" +
                "print('ENV='+os.environ['DATA_DIR'])\n");
        Map<String, byte[]> data = new LinkedHashMap<>();
        data.put("a.txt", "x".getBytes());
        ScriptRunnerService.ScriptRunResult r = newSvc().run("python", script, data);
        assertTrue(r.isPythonFound());
        assertEquals(0, r.getExitCode());
        assertTrue(r.getOutput().contains("ARGV="));
        assertTrue(r.getOutput().contains("ENV="));
        Files.deleteIfExists(script);
    }
}
