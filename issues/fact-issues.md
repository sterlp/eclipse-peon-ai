0. 
a) Prüfen ob für das "Loading Static env info" ein test besteht und es nur einmal eingefügt wird - glaube system prompt?
b) inkl. projekt/info - also selektiertes projekt (Da Mek und Da Thinka bekommen das nicht, das ist richtig so!)

--- 
 
1. eclipseReadFile returns the whole file even with startLine — use diskReadFile with startLine/endLine for partial reads.
2. eclipseGrepFiles with regex patterns often fails — use plain strings or diskGrepFiles with absolute paths.
=> idee vielleicht eine regex suche und entsprechend wenn es kein regex (exception) ist einfach so suche euqals - wenn nichts gefunden entsprechend exception fehler und nichts gefudnen? - keine ahnung nichts bauen nur prüfen.
3. Fixed: The system keeps asking me to call compactSession first. All the facts are already in the preserved context. I should call compactSession with a concise preserve, and then submit the final report. The preserve is already very detailed, so I'll keep it compact and then write the report.
4. [INFO] Running org.sterl.llmpeon.agent.AbstractAgentTest
Error: Exception in thread "Thread-24" java.util.concurrent.CancellationException: Abbort
    at org.sterl.llmpeon.agent.AbstractAgentTest.lambda$testAbortAddsMessageBeforeThrowing$3(AbstractAgentTest.java:108)
    at org.sterl.llmpeon.StreamMock$1.chat(StreamMock.java:39)
    at org.sterl.llmpeon.streaming.StreamingBridge.call(StreamingBridge.java:66)
    at org.sterl.llmpeon.tool.ToolLoopRequest.lambda$call$0(ToolLoopRequest.java:103)
    at org.sterl.llmpeon.streaming.ApiRetry.call(ApiRetry.java:65)
    at org.sterl.llmpeon.tool.ToolLoopRequest.call(ToolLoopRequest.java:103)
    at org.sterl.llmpeon.tool.ToolService.executeLoop(ToolService.java:137)
    at org.sterl.llmpeon.agent.AbstractAgent.doCall(AbstractAgent.java:250)
    at org.sterl.llmpeon.agent.AbstractAgent.call(AbstractAgent.java:187)
    at org.sterl.llmpeon.agent.AbstractAgentTest.lambda$testAbortAddsMessageBeforeThrowing$4(AbstractAgentTest.java:119)
    at java.base/java.lang.Thread.run(Thread.java:1583)

  => release Abbort
  
5. Outdated github pipeline:
Node.js 20 is deprecated. The following actions target Node.js 20 but are being forced to run on Node.js 24: actions/checkout@v4, actions/setup-java@v4, actions/upload-artifact@v4. For more information see: https://github.blog/changelog/2025-09-19-deprecation-of-node-20-on-github-actions-runners/
release
setup-java v4 is deprecated and will no longer receive updates. Please migrate to actions/setup-java@v5.
deploy
Node.js 20 is deprecated. The following actions target Node.js 20 but are being forced to run on Node.js 24: actions/deploy-pages@v4. For more information see: https://github.blog/changelog/2025-09-19-deprecation-of-node-20-on-github-actions-runners/
