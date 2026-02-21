package org.sterl.llmpeon.tool;

import org.junit.jupiter.api.Test;

class WebFetchToolTest {

    WebFetchTool subject = new WebFetchTool();
    @Test
    void test() throws Exception {
        System.err.println(subject.fetchAsMarkdown("https://docs.oracle.com/en/java/javase/21/docs/api/index.html"));
    }
    
    
    @Test
    void testLoadSonar() throws Exception {
        System.err.println(subject.fetchAsMarkdown("https://central.sonatype.com/artifact/dev.langchain4j/langchain4j"));
    }

}
