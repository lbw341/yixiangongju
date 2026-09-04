package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import java.io.IOException;
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

    private static boolean hasNonEmptyValue(String output, String prefix) {
        int idx = output.indexOf(prefix);
        if (idx < 0) return false;
        int start = idx + prefix.length();
        int end = output.indexOf("\n", start);
        String value = (end < 0 ? output.substring(start) : output.substring(start, end)).trim();
        return !value.isEmpty() && !"null".equals(value);
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
            assertTrue(hasNonEmptyValue(r.getOutput(), "ENVDIR="));
        }
        Files.deleteIfExists(script);
    }

    @Test
    void javaReadsEnv() throws Exception {
        CommandResolver cr = new CommandResolver();
        if (!cr.isJavaPresent()) {
            return;
        }
        Path tmpDir = Files.createTempDirectory("smoke_java");
        try {
            String javaHome = System.getenv("JAVA_HOME");
            String binDir = javaHome != null && !javaHome.isEmpty()
                    ? javaHome + "\\bin"
                    : null;
            String javacPath = binDir != null ? binDir + "\\javac" : "javac";
            String jarPath = binDir != null ? binDir + "\\jar" : "jar";

            Path src = tmpDir.resolve("Smoke.java");
            Files.writeString(src,
                    "public class Smoke {" + System.lineSeparator() +
                    "  public static void main(String[] a) {" + System.lineSeparator() +
                    "    System.out.println(\"DATA_DIR=\" + System.getenv(\"DATA_DIR\"));" + System.lineSeparator() +
                    "    System.out.println(\"INPUT_FILES=\" + System.getenv(\"INPUT_FILES\"));" + System.lineSeparator() +
                    "  }" + System.lineSeparator() +
                    "}" + System.lineSeparator());

            runAndWait(javacPath, tmpDir, src.toAbsolutePath().toString());
            if (!Files.exists(tmpDir.resolve("Smoke.class"))) {
                return;
            }

            Path manifest = tmpDir.resolve("MANIFEST.MF");
            Files.writeString(manifest,
                    "Manifest-Version: 1.0" + System.lineSeparator() +
                    "Main-Class: Smoke" + System.lineSeparator());
            Path jarFile = tmpDir.resolve("smoke.jar");
            runAndWait(jarPath, tmpDir, "cfm", jarFile.toAbsolutePath().toString(),
                    manifest.toAbsolutePath().toString(), "-C",
                    tmpDir.toAbsolutePath().toString(), "Smoke.class");
            if (!Files.exists(jarFile)) {
                return;
            }

            Map<String, byte[]> data = new LinkedHashMap<>();
            data.put("a.txt", "hello".getBytes());
            ScriptRunnerService.ScriptRunResult r = newSvc().runBy("java", jarFile, data);
            assertEquals(0, r.getExitCode());
            assertTrue(hasNonEmptyValue(r.getOutput(), "DATA_DIR="));
            assertTrue(hasNonEmptyValue(r.getOutput(), "INPUT_FILES="));
        } catch (IOException e) {
            System.out.println("javac/jar not available, skipping java E2E: " + e.getMessage());
            return;
        } finally {
            deleteRecursive(tmpDir);
        }
    }

    private static void runAndWait(String tool, Path dir, String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = tool;
        System.arraycopy(args, 0, cmd, 1, args.length);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getInputStream().readAllBytes();
        p.waitFor();
    }

    private static void deleteRecursive(Path path) throws IOException {
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
    void javaResolvesLibJarDependencyAtRuntime() throws Exception {
        CommandResolver cr = new CommandResolver();
        if (!cr.isJavaPresent()) {
            return;
        }
        Path tmpDir = Files.createTempDirectory("smoke_java_lib");
        try {
            String javaHome = System.getenv("JAVA_HOME");
            String binDir = javaHome != null && !javaHome.isEmpty() ? javaHome + "\\bin" : null;
            String javacPath = binDir != null ? binDir + "\\javac" : "javac";
            String jarPath = binDir != null ? binDir + "\\jar" : "jar";

            Path src = tmpDir.resolve("Smoke.java");
            Files.writeString(src,
                    "public class Smoke {" + System.lineSeparator() +
                    "  public static void main(String[] a) {" + System.lineSeparator() +
                    "    System.out.println(\"LIBOUT=\" + Lib.msg());" + System.lineSeparator() +
                    "  }" + System.lineSeparator() +
                    "}" + System.lineSeparator());
            Path libSrc = tmpDir.resolve("Lib.java");
            Files.writeString(libSrc,
                    "public class Lib {" + System.lineSeparator() +
                    "  public static String msg() { return \"hi-from-lib\"; }" + System.lineSeparator() +
                    "}" + System.lineSeparator());

            Path classes = tmpDir.resolve("classes");
            Files.createDirectories(classes);
            runAndWait(javacPath, tmpDir, "-d", classes.toAbsolutePath().toString(),
                    libSrc.toAbsolutePath().toString(), src.toAbsolutePath().toString());
            if (!Files.exists(classes.resolve("Smoke.class"))) {
                return;
            }

            Path manifest = tmpDir.resolve("MANIFEST.MF");
            Files.writeString(manifest,
                    "Manifest-Version: 1.0" + System.lineSeparator() +
                    "Main-Class: Smoke" + System.lineSeparator());
            Path appJar = tmpDir.resolve("app.jar");
            runAndWait(jarPath, classes, "cfm", appJar.toAbsolutePath().toString(),
                    manifest.toAbsolutePath().toString(), "-C",
                    classes.toAbsolutePath().toString(), "Smoke.class");
            Path libDir = Files.createDirectories(tmpDir.resolve("lib"));
            Path libJar = libDir.resolve("lib.jar");
            runAndWait(jarPath, classes, "cf", libJar.toAbsolutePath().toString(),
                    "-C", classes.toAbsolutePath().toString(), "Lib.class");
            if (!Files.exists(appJar) || !Files.exists(libJar)) {
                return;
            }

            Map<String, byte[]> data = new LinkedHashMap<>();
            data.put("a.txt", "hello".getBytes());
            ScriptRunnerService.ScriptRunResult r = newSvc().runBy("java", appJar, data);
            assertEquals(0, r.getExitCode(), "stderr/stdout was: " + r.getOutput());
            assertTrue(r.getOutput().contains("LIBOUT=hi-from-lib"), "output was: " + r.getOutput());
        } catch (IOException e) {
            System.out.println("javac/jar not available, skipping java lib E2E: " + e.getMessage());
            return;
        } finally {
            deleteRecursive(tmpDir);
        }
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
