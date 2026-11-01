package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiReviewAgent;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.open.aifactory.chat.dto.AgentAction;
import org.sterl.open.aifactory.chat.dto.AgentActionRequest;
import org.sterl.open.aifactory.chat.dto.AgentTileDto;
import org.sterl.open.aifactory.chat.dto.AgentTileState;
import org.sterl.open.aifactory.chat.dto.AgentTranscriptDto;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.chat.dto.TileCapability;
import org.sterl.open.aifactory.chat.dto.TranscriptLineDto;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.project.Project;
import org.sterl.open.aifactory.project.ProjectService;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * "Es gibt genau dieses eine Gespräch" (R-WP-11) is a statement about the API surface, so it is measured
 * on the surface: the handlers the running application actually registers, and the model types those
 * handlers can carry. A second conversation would have to arrive through one of the two — a path
 * segment, a request field, or a second runtime behind the same project — and each is refused here.
 *
 * <p>Two route families are frozen here, not one: the conversation edges of the Arch-Doc and the
 * <b>tile family</b> of Inc 5b/5c. The conversation side carries <b>three edges and four routes</b> since
 * Inc 5d: the edge that carries the conversation also carries its <b>end</b> ({@code POST …/messages/stop},
 * UC-WP-50) — one conversation per project still means one address per project, and the fourth route adds
 * neither a placeholder nor a body. That is a sharpening, not a loosening — a route that is in neither set
 * lands in the conversation bucket and fails it, and the tile family has its own exact path form. That form
 * is also where the <b>one</b> further placeholder is decided: {@code \{name\}} appears behind
 * {@code agents} and nowhere else, and every handler must still take exactly one {@code \{id\}}. What the
 * tile family may not do is stated positively at the end of the test: it knows one roster, it reads a tile
 * of it, and it refuses every other name. The stop is stated positively the same way: with no turn running
 * it refuses itself and opens nothing.</p>
 *
 * <p>Deliberately annotation-based, never parameter-name based: the aifactory build compiles without
 * {@code -parameters}, so parameter names are {@code arg0} and would prove nothing (AGENTS-DEV). The
 * field names of a record do not depend on that flag — they live in the class file.</p>
 */
@SpringBootTest
@Import(FakeGitConfig.class)
@Timeout(60)
class OneChatPerProjectTest {

    private static final String GIT_URL = "https://git.example.com/acme/one-chat.git";
    private static final String MODEL_PACKAGE = "org.sterl.open.aifactory.chat.dto";

    /** Only for the named send at the end: a label has to reach a model to prove what it does not do. */
    private static final MockLlmServer MODEL = new MockLlmServer(0);

    /** One project id, then one of the known conversation segments — nothing that could select a dialogue. */
    private static final Pattern ONE_PROJECT_PATH =
            Pattern.compile("^/api/projects/\\{id\\}/(messages|messages/stop|events)$");

    /**
     * The tile family (Inc 5b/5c, architecture doc "Die drei Gesprächs-Kanten bleiben drei"): one project
     * id and either the roster or <b>one tile of that same project</b>. {@code \{name\}} is the second
     * placeholder this application may have, and only ever behind {@code agents} — it names a tile to read,
     * never a conversation to open, which is what the path form below pins down.
     */
    private static final Pattern TILE_PATH = Pattern.compile("^/api/projects/\\{id\\}/agents(/\\{name\\})?$");

    private static final Set<Route> TILE_ROUTES = Set.of(
            new Route(HttpMethod.GET, "/api/projects/{id}/agents"),
            new Route(HttpMethod.POST, "/api/projects/{id}/agents"),
            new Route(HttpMethod.GET, "/api/projects/{id}/agents/{name}"));

    /**
     * The names a second conversation would have to use. A <b>user</b> concept is no longer one of them:
     * Inc 6 introduces it as a label a send carries with it (R-WP-17, R-WP-25), so {@code user} is allowed
     * on the one request type that sends — the field list below pins it there exactly, and the positive
     * check at the end of the test says what it may not do: choose nothing.
     */
    private static final Set<String> FORBIDDEN_FIELD_NAMES = Set.of(
            "chatId", "sessionId", "threadId", "conversationId", "dialogId",
            "username", "userName", "author", "owner");

    @TempDir
    static Path home;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ProjectService projects;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add("aifactory.home", home::toString);
    }

    /**
     * The positive check at the end sends, so this class needs a model: with no config no turn reaches a
     * provider and nothing lands in the memory — the label could then never be read back as the origin of
     * a line, which is exactly the statement the guard owes Inc 6.
     */
    @BeforeAll
    static void startModelAndConfig() throws IOException {
        MODEL.start();
        writeFactoryConfig();
    }

    @AfterAll
    static void stopModel() {
        MODEL.stop();
    }

    private static void writeFactoryConfig() throws IOException {
        Properties config = new Properties();
        config.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        config.setProperty(LlmConfigKeys.MODEL, "one-chat-guard");
        config.setProperty(LlmConfigKeys.URL, MODEL.getUrl());
        config.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (OutputStream out = Files.newOutputStream(home.resolve(FactoryHome.CONFIG_FILE))) {
            config.store(out, "one chat guard - the named send needs a model to reach");
        }
    }

    @Test
    // UC-WP-16
    void oneChatPerProject() {
        List<Class<?>> controllers = chatControllers();
        assertThat(controllers)
                .as("ein Handler-Exemplar entscheidet über den Chat, kein zweiter Angebotsweg")
                .extracting(Class::getSimpleName)
                .containsExactly("ChatController");

        List<Route> routes = routesOf(controllers);
        List<Route> tileFamily = routes.stream().filter(TILE_ROUTES::contains).toList();
        List<Route> conversation = routes.stream().filter(route -> !TILE_ROUTES.contains(route)).toList();
        assertThat(conversation)
                .as("genau die drei Gesprächskanten des Arch-Docs als vier Routen — jede weitere Kante"
                        + " wäre ein zweiter Gesprächsweg, auch unter einem neuen Pfad")
                .containsExactlyInAnyOrder(
                        new Route(HttpMethod.GET, "/api/projects/{id}/messages"),
                        new Route(HttpMethod.POST, "/api/projects/{id}/messages"),
                        new Route(HttpMethod.POST, "/api/projects/{id}/messages/stop"),
                        new Route(HttpMethod.GET, "/api/projects/{id}/events"));
        // with the set fixed there is nothing left to delete a conversation with, and the two POSTs are the
        // one that sends into it and the one that ends the turn that runs (UC-WP-50)
        assertThat(conversation)
                .allSatisfy(route -> assertThat(route.path()).as("route %s", route).matches(ONE_PROJECT_PATH));
        assertThat(tileFamily)
                .as("die Kachel-Familie ist die einzige Erweiterung und selbst eingefroren — sie adressiert"
                        + " eine Kachel dieses Projekts, nie ein Gespräch")
                .containsExactlyInAnyOrderElementsOf(TILE_ROUTES);
        assertThat(tileFamily)
                .allSatisfy(route -> assertThat(route.path()).as("route %s", route).matches(TILE_PATH));

        assertThat(handlersOf(controllers))
                .as("jeder Handler nimmt genau ein Projekt entgegen — ein zweiter Pfad-Platzhalter oder"
                        + " eine Request-Parameter wäre die Gesprächsauswahl")
                .allSatisfy(handler -> {
                    List<String> placeholders = pathVariableNames(handler);
                    // {name} ist der einzige weitere Platzhalter, und nur hinter agents: er nennt eine Kachel
                    // desselben Projekts, die gelesen wird (R-WP-26) — nie ein Gespräch, das geöffnet wird
                    assertThat(placeholders.stream().filter(name -> !"name".equals(name)).toList())
                            .as("%s nimmt genau ein {id} entgegen — genommen werden %s", handler.getName(),
                                    placeholders)
                            .containsExactly("id");
                    assertThat(annotatedWithSomethingElse(handler))
                            .as("%s nimmt nichts weiter entgegen", handler.getName())
                            .isEmpty();
                });

        Map<Class<?>, List<String>> modelFields = modelFieldsOf(controllers);
        assertThat(modelFields)
                .as("die Verlaufslesung, die Eingabe und die Kachel-Sprache sind die ganze Sprache"
                        + " dieser Kanten — ein weiterer Typ wäre ein weiterer Angebotsweg")
                .containsOnlyKeys(ChatMessageDto.class, SendMessageRequest.class, AgentTileDto.class,
                        AgentActionRequest.class, AgentTileState.class, TileCapability.class,
                        AgentTranscriptDto.class, TranscriptLineDto.class);
        // the third field is the author R-WP-4 asks for, and since Inc 6 it can name a person too
        // (R-WP-25) — the guard below is what these lists are for: any further field, whatever it is
        // called, is a second conversation
        assertThat(modelFields.get(ChatMessageDto.class)).containsExactly("role", "text", "origin");
        // genau ein Label-Feld für den Absender: die Eingabe hat weiter keinen Adressaten (UC-WP-6)
        assertThat(modelFields.get(SendMessageRequest.class)).containsExactly("text", "user");
        // a tile names an agent of this project's own team and carries numbers — no conversation, no author
        assertThat(modelFields.get(AgentTileDto.class)).containsExactly("name", "context", "tokens", "estimate",
                "contextPercent", "budget", "state", "capabilities");
        assertThat(modelFields.get(AgentActionRequest.class)).containsExactly("name", "action");
        // der Blick liest eine Kachel: ein Agentenname, ein Cap-Flag und Zeilen — kein Feld, das ein
        // Gespräch adressieren oder einen Autor erfinden könnte
        assertThat(modelFields.get(AgentTranscriptDto.class)).containsExactly("name", "truncated", "lines");
        assertThat(modelFields.get(TranscriptLineDto.class)).containsExactly("kind", "text");
        assertThat(modelFields.get(AgentTileState.class))
                .as("ein Kachel-Zustand ist ein geschlossenes Wertset")
                .containsExactly("IDLE", "WORKING", "COMPACTING", "ERROR");
        assertThat(modelFields.get(TileCapability.class))
                .as("TRANSCRIPT ist die einzige Fähigkeit, die hier nichts aufbaut (Inc 5c liest sie)")
                .containsExactly("CONTEXT", "CLEAR", "COMPACT", "TRANSCRIPT");
        Set<String> everyFieldOfATransportedType = new TreeSet<>();
        modelFields.values().forEach(everyFieldOfATransportedType::addAll);
        assertThat(everyFieldOfATransportedType)
                .as("kein Feld kann ein zweites Gespräch oder einen Nutzer wählen")
                .doesNotContain(FORBIDDEN_FIELD_NAMES.toArray(String[]::new));

        Project project = projects.add(GIT_URL).project();
        ProjectRuntime runtime = runtimes.runtime(project.id());
        assertThat(runtimes.runtimeCount())
                .as("das erste Fragen baut genau eine Runtime — das Projekt, nicht die Verbindung")
                .isEqualTo(1);

        SseEmitter firstConnection = runtimes.subscribe(project.id());
        SseEmitter secondConnection = runtimes.subscribe(project.id());
        assertThat(secondConnection)
                .as("die zweite Verbindung ist eine neue Leitung")
                .isNotSameAs(firstConnection);
        assertThat(runtimes.runtime(project.id()))
                .as("aber dasselbe Gespräch dahinter — es gibt nichts, was man neu öffnen könnte"
                        + ", ohne das Projekt zu wechseln")
                .isSameAs(runtime);
        assertThat(runtimes.runtimeCount())
                .as("auch die zweite Verbindung öffnet kein zweites Gespräch auf")
                .isEqualTo(1);
        assertThat(runtimes.history(project.id()))
                .as("und beide Leitungen lesen denselben Verlauf")
                .isEqualTo(runtime.history());

        // the positive replacement for „no second edge": the tile family can only address this project's
        // own roster, it never creates, selects or answers a conversation (R-WP-7, UC-WP-16)
        assertThat(runtimes.agents(project.id()))
                .as("die Kanten kennen genau Jons Team und seinen Such-Sub-Loop")
                .extracting(AgentTileDto::name)
                .containsExactly(AiPoAgent.NAME, AiPlanAgent.NAME, AiDevAgent.NAME, AiReviewAgent.NAME,
                        "Search");
        assertThat(runtimes.agents(project.id()))
                .filteredOn(tile -> "Search".equals(tile.name()))
                .as("der Sub-Loop bietet nichts an — also auch keinen Weg in ein zweites Gespräch")
                .singleElement()
                .satisfies(tile -> assertThat(tile.capabilities()).isEmpty());
        assertThatThrownBy(() -> runtimes.act(project.id(), "Fantom", AgentAction.CLEAR))
                .as("ein Name, der keine Kachel dieses Projekts ist, wird abgelehnt")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runtimes.transcript(project.id(), "Fantom"))
                .as("lesend gilt dieselbe Grenze — der Roster ist die einzige Adresse")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runtimes.transcript(project.id(), AiPoAgent.NAME))
                .as("Jons Gespräch ist der Chat selbst; der Blick öffnet sich nur vor Plan, Dev und Review")
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(runtimes.transcript(project.id(), AiDevAgent.NAME).lines())
                .as("der Blick liest eine Kachel, er baut nichts auf — schon gar kein zweites Gespräch")
                .isEmpty();
        // the positive replacement for the fourth route: the stop ends the one conversation's turn, it
        // cannot be aimed at another one and it opens nothing (UC-WP-50, UC-WP-16)
        assertThatThrownBy(() -> runtimes.stop(project.id()))
                .as("ohne laufenden Turn gibt es nichts zu stoppen — die Kante hat nichts auszuwählen")
                .isInstanceOf(IllegalStateException.class);
        assertThat(runtimes.history(project.id()))
                .as("ein abgelehnter Stop schreibt nichts in das Gespräch")
                .isEmpty();

        // the positive replacement for „no request field may name a user" (Inc 6): the label is allowed
        // because it chooses nothing — one project, one runtime, and the name comes back as the origin of
        // the line that carried it
        MODEL.queueResponse("Antwort an Anna");
        runtimes.submit(project.id(), "hallo", "Anna");
        awaitUntil(() -> runtimes.history(project.id()).stream()
                .anyMatch(line -> "Antwort an Anna".equals(line.text())), 20_000,
                "die benannte Eingabe ist beantwortet");
        assertThat(runtimes.history(project.id()))
                .as("das Label adressiert einen Menschen, nie ein Gespräch")
                .containsExactly(
                        ChatLine.user("hallo", "Anna"),
                        ChatLine.jon("Antwort an Anna", AiPoAgent.NAME));

        assertThat(runtimes.runtimeCount())
                .as("ein benanntes Senden öffnet kein zweites Gespräch auf")
                .isEqualTo(1);
    }

    /** Polls a condition with a deadline that fails as an assertion — a wait is never a proof (Inc 5). */
    private static void awaitUntil(BooleanSupplier condition, long timeoutMs, String what) {
        long until = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            assertThat(System.currentTimeMillis())
                    .as("Timeout: %s wurde nicht erreicht", what)
                    .isLessThan(until);
        }
    }

    // ------------------------------------------------------------------ surface

    private record Route(HttpMethod method, String path) {
    }

    /**
     * Every {@code @Controller} of the application that belongs to the chat component. The registered
     * type is read through {@link ClassUtils#getUserClass}: the container may hand out a proxy, and a
     * proxy has neither the route annotations nor the field names of the handlers it stands in for.
     */
    private List<Class<?>> chatControllers() {
        List<Class<?>> controllers = new ArrayList<>();
        for (String name : context.getBeanNamesForAnnotation(RestController.class)) {
            Class<?> registered = context.getType(name);
            if (registered == null) {
                continue;
            }
            Class<?> type = ClassUtils.getUserClass(registered);
            if (type.getPackageName().startsWith(ChatController.class.getPackageName())) {
                assertThat(type.getAnnotation(RequestMapping.class))
                        .as("eine Klassen-Präfix-Route würde die Pfade unter dem Tisch verlängern")
                        .isNull();
                controllers.add(type);
            }
        }
        return controllers;
    }

    private List<Route> routesOf(List<Class<?>> controllers) {
        List<Route> routes = new ArrayList<>();
        for (Class<?> controller : controllers) {
            for (Method method : handlersOf(List.of(controller))) {
                for (Annotation annotation : method.getAnnotations()) {
                    Route route = routeOf(annotation);
                    if (route == null && annotation.annotationType().getSimpleName().endsWith("Mapping")) {
                        fail("%s on %s#%s — ein neues Verb oder eine neue Pfadform braucht hier eine"
                                + " Entscheidung (UC-WP-16: genau ein Gespräch je Projekt)",
                                annotation.annotationType().getName(), controller.getSimpleName(),
                                method.getName());
                    }
                    if (route != null) {
                        routes.add(route);
                    }
                }
            }
        }
        return routes;
    }

    /** {@code null} for every annotation that is not a route. */
    private static Route routeOf(Annotation annotation) {
        if (annotation instanceof GetMapping get) {
            return new Route(HttpMethod.GET, single(get.path(), get.value()));
        }
        if (annotation instanceof PostMapping post) {
            return new Route(HttpMethod.POST, single(post.path(), post.value()));
        }
        if (annotation instanceof PutMapping put) {
            return new Route(HttpMethod.PUT, single(put.path(), put.value()));
        }
        if (annotation instanceof DeleteMapping delete) {
            return new Route(HttpMethod.DELETE, single(delete.path(), delete.value()));
        }
        if (annotation instanceof PatchMapping patch) {
            return new Route(HttpMethod.PATCH, single(patch.path(), patch.value()));
        }
        return null;
    }

    private static String single(String[] path, String[] value) {
        if (path.length > 0) {
            return path[0];
        }
        return value.length > 0 ? value[0] : "";
    }

    private List<Method> handlersOf(List<Class<?>> controllers) {
        List<Method> handlers = new ArrayList<>();
        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                boolean routed = Arrays.stream(method.getAnnotations())
                        .anyMatch(annotation -> routeOf(annotation) != null);
                if (routed) {
                    handlers.add(method);
                }
            }
        }
        return handlers;
    }

    /** The declared path placeholders, named as the handler names them — the build has no {@code -parameters}. */
    private static List<String> pathVariableNames(Method handler) {
        List<String> names = new ArrayList<>();
        for (Parameter parameter : handler.getParameters()) {
            PathVariable variable = parameter.getAnnotation(PathVariable.class);
            if (variable == null) {
                continue;
            }
            assertThat(variable.value())
                    .as("ein Platzhalter ohne eigenen Namen läuft ins Leere — dieser Build hat kein -parameters")
                    .isNotBlank();
            names.add(variable.value());
        }
        return names;
    }

    /** Anything a caller could address besides the path: a query, a header, a cookie. */
    private static List<String> annotatedWithSomethingElse(Method handler) {
        List<String> found = new ArrayList<>();
        for (Parameter parameter : handler.getParameters()) {
            for (Annotation annotation : parameter.getAnnotations()) {
                Class<? extends Annotation> type = annotation.annotationType();
                if (type == RequestParam.class || type == RequestHeader.class || type == CookieValue.class) {
                    found.add(type.getSimpleName() + " " + parameter);
                }
            }
        }
        return found;
    }

    /**
     * The model types that can cross these handlers, with their field names — the honest whole of it: a
     * type no handler uses cannot start a conversation, and a type a handler uses is covered here.
     */
    private Map<Class<?>, List<String>> modelFieldsOf(List<Class<?>> controllers) {
        Collection<Class<?>> modelTypes = new LinkedHashSet<>();
        for (Class<?> controller : controllers) {
            for (Method handler : handlersOf(List.of(controller))) {
                for (Parameter parameter : handler.getParameters()) {
                    collectModelTypes(parameter.getParameterizedType(), modelTypes);
                }
                collectModelTypes(handler.getGenericReturnType(), modelTypes);
            }
        }
        Map<Class<?>, List<String>> fields = new LinkedHashMap<>();
        for (Class<?> modelType : modelTypes) {
            if (modelType.isEnum()) {
                // an enum cannot name a conversation: its wire values are a closed set, and that set is
                // read here as if it were a field list, so the forbidden-name check below covers it too
                fields.put(modelType, Arrays.stream(modelType.getEnumConstants())
                        .map(value -> ((Enum<?>) value).name())
                        .toList());
                continue;
            }
            assertThat(modelType.isRecord())
                    .as("%s überträgt das Gespräch als Record", modelType.getSimpleName())
                    .isTrue();
            fields.put(modelType, Arrays.stream(modelType.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList());
        }
        return fields;
    }

    private static void collectModelTypes(Type type, Collection<Class<?>> into) {
        if (type instanceof Class<?> typeClass) {
            if (MODEL_PACKAGE.equals(typeClass.getPackageName())) {
                into.add(typeClass);
            }
            if (typeClass.isRecord()) {
                Arrays.stream(typeClass.getRecordComponents())
                        .forEach(component -> collectModelTypes(component.getGenericType(), into));
            }
        } else if (type instanceof ParameterizedType parameterized) {
            collectModelTypes(parameterized.getRawType(), into);
            Arrays.stream(parameterized.getActualTypeArguments())
                    .forEach(argument -> collectModelTypes(argument, into));
        }
    }
}
