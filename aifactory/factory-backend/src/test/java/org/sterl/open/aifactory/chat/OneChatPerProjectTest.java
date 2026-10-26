package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

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

import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.project.Project;
import org.sterl.open.aifactory.project.ProjectService;

/**
 * "Es gibt genau dieses eine Gespräch" (R-WP-11) is a statement about the API surface, so it is measured
 * on the surface: the handlers the running application actually registers, and the model types those
 * handlers can carry. A second conversation would have to arrive through one of the two — a path
 * segment, a request field, or a second runtime behind the same project — and each is refused here.
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

    /** One project id, then one of the three known segments — nothing that could select a dialogue. */
    private static final Pattern ONE_PROJECT_PATH =
            Pattern.compile("^/api/projects/\\{id\\}/(messages|events)$");

    /** The names a second conversation or a user concept would have to use. */
    private static final Set<String> FORBIDDEN_FIELD_NAMES = Set.of(
            "chatId", "sessionId", "threadId", "conversationId", "dialogId",
            "user", "username", "userName", "author", "owner");

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

    @Test
    // UC-WP-16
    void oneChatPerProject() {
        List<Class<?>> controllers = chatControllers();
        assertThat(controllers)
                .as("ein Handler-Exemplar entscheidet über den Chat, kein zweiter Angebotsweg")
                .extracting(Class::getSimpleName)
                .containsExactly("ChatController");

        List<Route> routes = routesOf(controllers);
        assertThat(routes)
                .as("genau die drei Kanten des Arch-Docs — jede weitere wäre ein zweiter Gesprächsweg")
                .containsExactlyInAnyOrder(
                        new Route(HttpMethod.GET, "/api/projects/{id}/messages"),
                        new Route(HttpMethod.POST, "/api/projects/{id}/messages"),
                        new Route(HttpMethod.GET, "/api/projects/{id}/events"));
        // with the set fixed there is nothing left to delete a conversation with, and the only POST is
        // the one that sends into the existing conversation
        assertThat(routes)
                .allSatisfy(route -> assertThat(route.path()).as("route %s", route).matches(ONE_PROJECT_PATH));

        assertThat(handlersOf(controllers))
                .as("jeder Handler nimmt genau ein Projekt entgegen — ein zweiter Pfad-Platzhalter oder"
                        + " eine Request-Parameter wäre die Gesprächsauswahl")
                .allSatisfy(handler -> {
                    assertThat(pathVariableCount(handler))
                            .as("%s nimmt genau ein {id} entgegen", handler.getName())
                            .isEqualTo(1);
                    assertThat(annotatedWithSomethingElse(handler))
                            .as("%s nimmt nichts weiter entgegen", handler.getName())
                            .isEmpty();
                });

        Map<Class<?>, List<String>> modelFields = modelFieldsOf(controllers);
        assertThat(modelFields)
                .as("die Verlaufslesung und die Eingabe sind die ganze Gesprächs-Sprache")
                .containsOnlyKeys(ChatMessageDto.class, SendMessageRequest.class);
        // the third field is the author R-WP-4 asks for — the guard below is what this list is for: a
        // fourth field, whatever it is called, is a second conversation or a user concept
        assertThat(modelFields.get(ChatMessageDto.class)).containsExactly("role", "text", "origin");
        assertThat(modelFields.get(SendMessageRequest.class)).containsExactly("text");
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

    private static long pathVariableCount(Method handler) {
        return Arrays.stream(handler.getParameters())
                .filter(parameter -> parameter.isAnnotationPresent(PathVariable.class))
                .count();
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
