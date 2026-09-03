package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptPackageServiceTest {

    @Test
    void entryCandidatesContainJsForNode() {
        assertTrue(java.util.Arrays.asList(ScriptPackageService.entryCandidatesFor("node")).contains("main.js"));
    }

    @Test
    void entryCandidatesPyForPython() {
        assertTrue(java.util.Arrays.asList(ScriptPackageService.entryCandidatesFor("python")).contains("main.py"));
    }

    @Test
    void entryCandidatesJarForJava() {
        assertTrue(java.util.Arrays.asList(ScriptPackageService.entryCandidatesFor("java")).contains("app.jar"));
    }
}
