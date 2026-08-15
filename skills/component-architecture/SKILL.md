# Domain Model & Architecture Rules

Domain conventions, component architecture, development practices.

## Table of Contents

- [TL;DR](#tldr)
- [Overview](#overview)
- [Package Structure](#package-structure)
- [APIs](#apis)
- [Services](#services)
- [Components](#components)
- [Repositories](#repositories)
- [Models and Entities](#models-and-entities)

---

## TL;DR

Components are isolated in packages. **Access ONLY through Service classes.**

**Golden Rule:** Service = public API. Everything else = private.

Key decisions:
1. `@Service` + `@Transactional(timeout=10_000)` only if persistence is needed
2. Service returns Entity (persistence) or DTO (connectors), Resource converts
3. Service starts Transaction (TRX, 10s) if needed, Component participates (MANDATORY)
4. Extract to Component: >20 lines, reused, private, testable -- or in long Service classes
5. Clean code and SOLID principals apply where useful
    a) Flow of dependency flows always into one direction
    b) never have circular dependencies

---

## Overview

### Layering and access rules

| Layer | Package | Suffix | Annotation | Access Rules |
|-------|---------|--------|------------|--------------|
| Service | root | `*Service` | `@Service` (+ `@Transactional` if persistence) | Public API between the components |
| Component | `component` | `*Component` | `@Component` (+ MANDATORY TX) | Private, called by service or other components in the module |
| Repository | `repository` | `*Repository` | `@Repository` | Called by Service/Component in same module |
| Entity | `model` | `*Entity` | `@Entity` | Shared across components (read-only) |
| API Model | `api.<module>.model` | No suffix | - | External representation |
| Resource | `api` | `*Resource` | `@RestController` | Delegates to Service, converts Entity↔API |
| Timer | root / `timer` | `*Timer` | `@Component` + `@Scheduled` | Delegates to Service |

**Flow:** Service starts → Component participates (Transaction) → Repository operates  
**Conversion:** Service returns Entity → Resource converts → API model

A module contains:
1. Always a service and an own purpose / business functionality
2. own components if needed
3. own model classes which this service is responsible for
4. and so on-




### Timer classes

**Package:** root or `timer`  
**Annotation:** `@Scheduled`  
**Rule:** No logic. Delegate to Service.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PersonCleanupTimer {
    private final PersonService personService;

    @Scheduled(cron = "0 0 2 * * ?")  // 2 AM daily
    public void cleanupInactivePersons() {
        personService.deleteInactivePersons();
        log.info("Cleanup finished");
    }
}
```

---

### General rules

- Clean Code + SOLID where useful
- **>4 params:** Use Value/Entity class
- **REST:** Zalando API Guidelines
- **Pre-commit:** `mvn clean install`

```java
// ❌
public void create(String a, String b, String c, String d, String e);

// ✅
public void create(PersonData data);
```

---

### Testing

One test class per production class.

**Subject:** Variable named `subject`  
**Pattern:** GIVEN/WHEN/THEN comments

```java
@Test
void shouldCreatePersonSuccessfully() {
    // GIVEN: Setup code and test data
    String firstName = "Max";
    String lastName = "Mustermann";

    // WHEN: Action / modification code calling the service/Component
    Person result = subject.createPerson(firstName, lastName);

    // THEN: Assert code - verify expected outcome
    assertThat(result).isNotNull();
    assertThat(result.getFirstName()).isEqualTo(firstName);
    assertThat(result.getLastName()).isEqualTo(lastName);
}
```

**Random data:** No `@BeforeEach` cleanup needed

```java
@Test
void shouldFindPersonByEmail() {
    // GIVEN: Random test data
    String email = "test-" + UUID.randomUUID() + "@example.com";
    PersonEntity person = createRandomPerson(email);
    personRepository.save(person);

    // WHEN
    Optional<PersonEntity> result = subject.findByEmail(email);

    // THEN
    assertThat(result).isPresent();
    assertThat(result.get().getEmail()).isEqualTo(email);
}
```

**Test DB:** H2 in-memory (`testdb`)

---

## Package Structure

```
org.sterl.foo.person/
├── PersonService.java           # Public API (root package)
├── api/
│   ├── PersonResource.java      # REST endpoint
│   ├── model/
│   │   └── Person.java          # API class (no suffix)
│   └── converter/
│       └── PersonEntityToPersonConverter.java
├── component/
│   ├── CreatePersonComponent.java
│   └── DeletePersonComponent.java
├── model/
│   └── PersonEntity.java        # Entity (with suffix)
├── repository/
│   └── PersonRepository.java
└── timer/
    └── PersonCleanupTimer.java
```

All classes except Model/Entity classes and Converters are considered **private** to the component.

---

## APIs

### API models

**Package:** `api.<module>.model`  
**Naming:** NO suffix  
**Purpose:** External REST representation

```java
public class Person {
    private Long id;
    private String firstName;
    private String lastName;
}
```

---

### Entity → API conversion

**Option 1: Converter** (complex mappings)

```java
@Component
public class PersonEntityToPersonConverter implements Converter<PersonEntity, Person> {
    public Person convert(PersonEntity entity) {
        return new Person(entity.getId(), entity.getFirstName(), entity.getLastName());
    }
}
```

**Location:**
- `api/converter/` - reused by multiple Resources
- `api/` - single Resource only
- Never in Service/Component

**Option 2: Projections** (simple mappings)

Spring Data interface projections - when 1:1 mapping.

---

### REST resources

**Package:** `api`  
**Naming:** `*Resource` suffix  
**Role:** Anti-corruption layer - public REST API  
**Responsibility:** Delegate to Service, convert Entity↔API, no logic

```java
@RestController
@RequestMapping("/api/persons")
@RequiredArgsConstructor
public class PersonResource {
    private final PersonService personService;
    private final Converter<PersonEntity, Person> entityToApiConverter;

    @GetMapping("/{id}")
    public Person getPerson(@PathVariable Long id) {
        PersonEntity entity = personService.findPerson(id); // Service returns Entity
        return entityToApiConverter.convert(entity); // Resource converts to API
    }
}
```

---

## Services

**Package:** root  
**Annotation:** `@Service` (+ `@Transactional(timeout=10_000)` if persistence needed)  
**Role:** Public API, workflow orchestration

**Characteristics:**
- Workflow + high-level logic
- Cross-cutting: Transaction (TRX, if persistence), auth, caching, policies
- Generic method names (`savePerson()`, `findPerson()`)
- No private methods → extract to Component (except trivial helpers)
- Returns Entities (not API models) - or Pojos for non-persistence Services

**May call:** Services (other components), own Components, own Repositories
**JavaDoc:** Short responsibility statement required what it is managing

```java
@Service
@Transactional(timeout = 10_000)
@RequiredArgsConstructor
public class PersonService {
    private final CreatePersonComponent createPersonComponent;

    public PersonEntity createPerson(String firstName, String lastName) {
        return createPersonComponent.call(firstName, lastName);
    }
}
```

**Direct Repository call** (simple ops):

```java
@Service
@Transactional(timeout = 10_000)
public class PersonService {
    public PersonEntity findPerson(Long id) {
        return personRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Person not found: " + id));
    }
}
```

**When to delegate vs inline:**
- **Use Component:** Logic >10 lines, reused, needs testing, complex rules
- **Inline:** Simple CRUD, single-line, no logic

---

## Components

**Package:** `component`  
**Annotation:** `@Component` = `@Component + MANDATORY TX`  
**Role:** Single use-case, low-level logic

**Characteristics:**
- ONE method: `call()`
- Specific method names OK (`validateEmailFormat()`)
- Testable in isolation
- Needs existing Transaction (TRX) from Service

**May call:** Components (same module), Services (other components)  
**May NOT:** Own Service (no upward), foreign Repositories

```java
@Component
@RequiredArgsConstructor
public class DeletePersonComponent {
    private final PersonRepository personRepository;

    public void call(Long personId) {
        PersonEntity person = personRepository.findById(personId)
            .orElseThrow(() -> new EntityNotFoundException("Person not found"));
        personRepository.delete(person);
    }
}
```

---


## Transaction Management

### Service Transaction (TRX)

`@Transactional(timeout = 10_000)` - starts transaction, 10s timeout

**Use only when Service does persistence operations.** Services without DB access (REST/SOAP connectors, facades) don't need `@Transactional`.

Use `@Transactional(readOnly = true)` for queries - Hibernate optimizes flush.

```java
// Persistence Service - needs TX
@Service
@Transactional(timeout = 10_000)  // writes
public class PersonService { }

@Service
@Transactional(readOnly = true, timeout = 10_000)   // reads
public class PersonQueryService { }

// Connector Service - NO Transaction
@Service
public class SoapSender {  // HTTP calls, no persistence
    public void send(String data) { }
}
```

### Component Transaction (TRX)

Is managed by the service.

```java
// ✅ Correct - called from Service with transaction
@Service
@Transactional(timeout = 10_000)
public class PersonService {
    private final CreatePersonComponent createPersonComponent;

    public PersonEntity createPerson(String firstName, String lastName) {
        return createPersonComponent.call(firstName, lastName); // Works - transaction exists
    }
}
```

---

## Repositories

**Package:** `repository`  
**Access:** Service/Component in same module only

```java
@Repository
public interface PersonRepository extends JpaRepository<PersonEntity, Long> {
    List<PersonEntity> findByLastName(String lastName);
}
```

Role: Persistence abstraction

---

## Models and Entities

### Entities

**Package:** `model`  
**Naming:** `*Entity` suffix (avoids collision with API classes)

```java
@Entity
@Table(name = "person")
public class PersonEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
```

---

### Database conventions

- **Tables:** Lowercase (`person`, `order_item`)
- **Indexes:** Lowercase (`idx_person_email`)
- **Columns:** Lowercase, snake_case
- **Test DB:** H2 in-memory
- **Hibernate:** `create-drop` (dev/test)


---

### Custom Annotations

#### Component with mandatory transaction

```java
@Transactional(propagation = Propagation.MANDATORY)
@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FooComponent {}
```

The name of the component is usually like a method name e.g:
- CreateBillComponent
- ReadPdfComponent

**Why MANDATORY?**
A component usually doesn't handle DB transactions. If a transaction is required by default it should be managed by the Service and be requested as `MANDATORY` to avoid many small transactions (or even a wrong transaction scope).
Think: "private method" extracted from Service.

---

## Comments rules

1. Always try to explain yourself in code
2. Don't be redundant
3. Don't add obvious noise
4. Use as explanation of intent
5. Use as clarification of code
6. Use as warning of consequences
7. Don't duplicate docs, link to it