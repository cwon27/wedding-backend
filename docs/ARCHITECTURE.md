# 픽셀 웨딩 방명록 — 구조와 동작 설명 (백엔드)

> 프론트엔드 설명은 `wedding-frontend` 레포의 `docs/ARCHITECTURE.md` 에 있다.
> 이 문서는 2026-11-11 기준 develop 브랜치의 코드를 설명한다. 구조가 바뀌면 함께 고친다.

## 목차

0. [큰 그림](#0-큰-그림)
1. [레포 구조](#1-레포-구조)
2. [요청이 흐르는 계층](#2-요청이-흐르는-계층)
3. [설정](#3-설정-applicationyml--env--appproperties)
4. [DB 와 Flyway](#4-db-와-flyway)
5. [Entity 와 Repository](#5-entity-와-repository-jpa)
6. [로그인 없이 기기 구분](#6-로그인-없이-기기-구분-device)
7. [캐릭터 API](#7-캐릭터-api-character)
8. [이미지 생성 대기열](#8-이미지-생성-대기열-generation)
9. [저장소와 오류 처리](#9-저장소와-오류-처리)
10. [테스트](#10-테스트)
11. [요청 하나를 끝까지 따라가기](#11-요청-하나를-끝까지-따라가기)
12. [현재 상태와 알려진 약점](#12-현재-상태와-알려진-약점)
13. [실행 방법](#13-실행-방법)
14. [공부 순서와 확인 질문](#14-공부-순서와-확인-질문)

---

## 0. 큰 그림

하객 입장에서 보면:

1. 청첩장 링크를 열면 식장 배경이 나오고, 다른 하객들의 픽셀 캐릭터가 돌아다닌다.
2. "캐릭터 만들기"로 사진, 이름, 축하 메시지를 올린다.
3. 잠시 뒤 나를 닮은 후보 4개가 나온다. 마음에 안 들면 한 번 다시 만들 수 있다.
4. 하나를 고르면 걷기 애니메이션이 만들어지고, 정원에 입장해 말풍선으로 메시지를 보여 준다.

```
┌─────────────── 휴대폰 브라우저 ───────────────┐
│ guest-web (하객 화면)     admin-web (관리자)   │   ← wedding-frontend 레포
└──────────────┬────────────────────────────────┘
               │ HTTP (/api/...)
┌──────────────▼────────────────────────────────┐
│ Spring Boot 백엔드                              │   ← wedding-backend 레포 (이 레포)
│  - API (캐릭터 만들기, 목록)                     │
│  - 대기열 워커 (이미지 생성은 오래 걸려서 뒤에서 처리) │
└───────┬──────────────┬──────────────┬─────────┘
        │              │              │
   Supabase DB    이미지 저장소      AI 서비스
   (PostgreSQL)   (지금: 로컬 폴더    (Gemini, 나중에 PixelLab)
                   나중: S3)
```

**핵심 설계**: AI 이미지 생성은 수십 초가 걸린다. 하객 요청을 그동안 붙잡지 않도록
**"작업을 대기열에 넣고 바로 응답 → 뒤에서 워커가 처리 → 화면은 주기적으로 상태 확인"** 구조로 만들었다.

---

## 1. 레포 구조

프론트와 백엔드는 배포되는 곳이 달라서(프론트: S3, 백엔드: EC2 예정) 레포를 나눴다.

```
wedding-backend/
├─ src/main/java/com/wedding/guestbook/
│  ├─ character/       하객 캐릭터 (API, 규칙, DB 접근)
│  ├─ generation/      이미지 생성 대기열, 워커, 생성기, 후처리
│  │  └─ gemini/       Gemini 후보 생성기
│  ├─ device/          로그인 없이 기기 구분 (쿠키)
│  ├─ guestbook/       방명록(부부 정보)
│  ├─ storage/         이미지 파일 저장
│  ├─ common/          공통 오류 처리
│  └─ config/          설정값, CORS
├─ src/main/resources/
│  ├─ application.yml  설정 파일
│  └─ db/migration/    테이블 정의 SQL (Flyway)
├─ src/test/           테스트
├─ docs/               이 문서
├─ .env                DB 비밀번호, API 키 (git 에 올라가지 않음)
├─ .env.example        .env 양식 (git 에 올라감)
├─ Dockerfile, docker-compose.yml   배포용 (아직 사용 전)
└─ build.gradle        라이브러리 목록
```

### 패키지를 나누는 방식

지금은 **기능별(package by feature)** 로 나뉘어 있다. 다른 방식과 비교하면:

| | 계층별 (controller/, service/, dto/ …) | 기능별 (character/, generation/ …) | 절충형 (character/controller/ …) |
|---|---|---|---|
| 처음 보는 사람 | 익숙한 구조 | 기능을 알면 쉬움 | 쉬움 |
| 기능 하나 수정 | 폴더 여러 개를 오감 | 한 폴더 안에서 끝남 | 한 기능 폴더 안에서 끝남 |
| 프로젝트가 커질 때 | service/ 에 파일이 수십 개 섞임 | 기능 단위로 유지 | 기능 단위로 유지 |
| 많이 쓰는 곳 | SI, 전통적인 회사 프로젝트 | 스프링 공식 예제, DDD | 국내 서비스 회사에서 흔함 |

둘 다 실무에서 쓰는 방식이고 정답은 없다. 이 프로젝트는 절충형으로 바꾸는 것을 검토 중이다.

---

## 2. 요청이 흐르는 계층

```
HTTP 요청 → Filter → Controller → Service → Repository → DB
                       (입구)      (규칙)      (DB 접근)
```

| 계층 | 예 | 하는 일 |
|---|---|---|
| Filter | `DeviceIdFilter` | 모든 요청 앞단의 공통 처리 (기기 쿠키 발급) |
| Controller | `CharacterController` | URL 과 메서드를 연결, 요청을 받아 응답 형식으로 돌려줌 |
| Service | `CharacterService` | "기기당 1개", "재생성 1회" 같은 업무 규칙 |
| Repository | `GuestCharacterRepository` | DB 조회와 저장 |
| Entity | `GuestCharacter` | DB 테이블 한 줄을 자바 객체로 표현 |

규칙을 컨트롤러가 아닌 서비스에 모으는 이유: 한곳에 있어야 테스트하기 쉽고, 다른 API(예: 관리자)에서도 재사용할 수 있다.

---

## 3. 설정: application.yml + .env + AppProperties

```yaml
spring:
  config:
    import: optional:file:.env[.properties]   # .env 파일을 설정값으로 읽음
  datasource:
    url: ${DB_URL}                            # .env 의 DB_URL 값으로 치환
app:
  guestbook:
    max-regenerations: 1
  generation:
    candidates: ${CANDIDATE_GENERATOR:fake}   # 값이 없으면 기본값 fake
```

- `${이름:기본값}`: 환경변수나 `.env` 에서 값을 가져오고, 없으면 기본값을 쓴다.
- 비밀번호와 API 키는 `.env` 에만 둔다. `application.yml` 은 GitHub 에 올라가지만 `.env` 는 `.gitignore` 에 있어 올라가지 않는다.

`app.*` 값은 `AppProperties` record 하나로 묶어 쓴다.

```java
@ConfigurationProperties("app")
public record AppProperties(Cors cors, Device device, Guestbook guestbook, Generation generation) { ... }
```

`@Value("${app...}")` 문자열을 곳곳에 쓰면 오타를 알아채기 어렵다. 타입이 있는 객체로 모아
`props.guestbook().maxRegenerations()` 처럼 쓴다.

---

## 4. DB 와 Flyway

테이블은 `db/migration/V1__init.sql` 에 있다.

| 테이블 | 담는 것 | 핵심 컬럼 |
|---|---|---|
| `guestbook` | 방명록 (부부 1쌍 = 1줄) | 신랑·신부 이름, 예식 일시, 상태(OPEN/READ_ONLY), 최대 캐릭터 수 |
| `guest_character` | 하객 캐릭터 | 기기 ID, 이름, 메시지, 상태, 재생성 횟수, 사진·이미지·스프라이트 경로 |
| `character_candidate` | 후보 이미지 | 어느 캐릭터의 몇 회차(round) 몇 번째(slot)인지 |
| `generation_job` | 이미지 생성 대기열 | 작업 종류(CANDIDATES/SPRITE), 상태(QUEUED/RUNNING/DONE/FAILED), 시도 횟수 |

가장 중요한 제약:

```sql
create table guest_character (
    ...
    unique (guestbook_id, device_id)   -- 한 방명록에서 기기당 캐릭터 1개
);
```

"기기당 1개"를 코드뿐 아니라 DB 에서도 강제한다. 코드에 버그가 있거나 요청이 동시에 와도 DB 가 두 번째 저장을 거부한다.

**Flyway** 는 이 SQL 파일들을 자동 실행하는 도구다. 서버가 시작할 때 `flyway_schema_history` 를 보고 아직 실행하지 않은 파일만 실행한다.
테이블을 바꾸려면 기존 파일을 고치지 말고 `V3__...sql` 을 새로 추가한다 (실행된 파일을 고치면 Flyway 가 오류를 낸다).

- `V2__seed_guestbook.sql`: 방명록 데이터(문형준 ♥ 김채송, 2027-04-10 11:00 KST)
- Supabase 의 "automatic RLS" 옵션이 public 스키마에 함수(`rls_auto_enable`)를 만들어 두어 Flyway 가 "비어 있지 않은 DB" 라며 멈췄다.
  `baseline-on-migrate: true` + `baseline-version: 0` 으로 "현재 상태를 버전 0 으로 보고 V1 부터 실행" 하게 했다.
  (`baseline-version` 을 기본값 1 로 두면 V1 이 건너뛰어진다.)

---

## 5. Entity 와 Repository (JPA)

```java
@Entity
@Table(name = "guest_character")
public class GuestCharacter {
    @Id private UUID id;
    private String name;
    @Enumerated(EnumType.STRING) private CharacterStatus status;   // DB 에 "CHOOSING" 같은 문자열로 저장
    private int regenCount;                                         // 컬럼 regen_count 에 자동 대응
    ...
}
```

Repository 는 인터페이스만 선언하면 스프링이 구현을 만든다.

```java
public interface GuestCharacterRepository extends JpaRepository<GuestCharacter, UUID> {
    Optional<GuestCharacter> findByGuestbookIdAndDeviceId(Long guestbookId, String deviceId);
    // → where guestbook_id = ? and device_id = ?
}
```

`ddl-auto: validate`: JPA 가 테이블을 만들지 않고, 엔티티와 테이블이 맞는지만 검사한다. 테이블은 Flyway 만 관리한다.

### 캐릭터 상태 흐름 (`CharacterStatus`)

백엔드와 프론트 전체의 기준이다.

```
PENDING_CANDIDATES ──(워커: 후보 4장)──▶ CHOOSING ──(하객 선택)──▶ PENDING_SPRITE ──(워커: 걷기 시트)──▶ COMPLETE
        ▲                                   │
        └────────(다시 만들기, 1회)───────────┘
                (재시도 3번 다 실패하면 FAILED)
```

---

## 6. 로그인 없이 기기 구분: device/

`DeviceIdFilter` 가 `/api/` 요청마다 동작한다.

```java
String deviceId = readCookie(request, "wg_device")
        .filter(DeviceIdFilter::isValid)              // UUID 형식인지
        .orElseGet(() -> {                            // 쿠키가 없으면 새로 발급
            String issued = UUID.randomUUID().toString();
            ResponseCookie cookie = ResponseCookie.from("wg_device", issued)
                    .httpOnly(true)                   // 자바스크립트로 읽거나 조작 불가
                    .maxAge(...)                      // 1년
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            return issued;
        });
request.setAttribute(ATTRIBUTE, deviceId);           // 컨트롤러가 꺼내 쓰도록 요청에 붙임
```

`@DeviceId` + `DeviceIdArgumentResolver` 는 컨트롤러에서 편하게 꺼내 쓰기 위한 장치다.

```java
public MyCharacterResponse mine(@DeviceId String deviceId) { ... }
```

스프링이 컨트롤러를 부를 때 `@DeviceId` 파라미터를 리졸버에게 채워 달라고 하고, 리졸버는 필터가 붙여 둔 값을 넣는다.

**한계**: 카카오톡 인앱 브라우저와 Safari 는 쿠키를 따로 쓰고, 시크릿 모드도 새 기기로 보인다.
결혼식 하객 용도라 허용하고, 대신 방명록 최대 인원(`max_characters`)으로 비용 상한을 둔다.
IP 차단(식장 와이파이를 같이 씀)이나 브라우저 지문(같은 기종 아이폰끼리 겹침)은 정상 하객을 막을 수 있어 쓰지 않는다.

---

## 7. 캐릭터 API: character/

### API

| API | 하는 일 |
|---|---|
| `GET /api/characters?since=...` | 정원에 보여줄 캐릭터 목록 (바뀐 것만) |
| `POST /api/characters` | 사진, 이름, 메시지 업로드 → 후보 생성 작업 등록 |
| `GET /api/characters/me` | 내 캐릭터 진행 상태 (대기 순번 포함) |
| `POST /api/characters/me/regenerate` | 후보 다시 만들기 (기기당 1회) |
| `POST /api/characters/me/choose` | 후보 선택 → 걷기 시트 생성 작업 등록 |

### since 커서 폴링

하객 화면은 10초마다 새 캐릭터를 확인한다. 매번 전체를 받지 않도록 응답에 `syncCursor`(마지막으로 바뀐 시각)를 주고,
다음 요청에서 `since=그 시각` 을 보내면 그 뒤로 바뀐 것만 돌려준다.

```java
List<GuestCharacter> changed = since == null
        ? characters.findByGuestbookIdOrderByUpdatedAtAsc(guestbookId)
        : characters.findByGuestbookIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtAsc(guestbookId, since);
```

"같거나 큰"(`GreaterThanEqual`)을 쓰는 이유: 같은 시각에 두 캐릭터가 바뀌었을 때 하나를 놓치지 않기 위해서. 중복은 프론트가 id 로 덮어쓴다.

### 만들기: create()

```java
if (guestbook.getStatus() == READ_ONLY) throw conflict("GUESTBOOK_CLOSED", ...);
if (characters.findByGuestbookIdAndDeviceId(...).isPresent()) throw conflict("ALREADY_CREATED", ...);
if (characters.countByGuestbookId(...) >= guestbook.getMaxCharacters()) throw conflict("GUESTBOOK_FULL", ...);
storage.put(photoKey, bytes(photo), ...);                         // 사진 저장
characters.saveAndFlush(character);                               // PENDING_CANDIDATES 로 저장
jobs.save(GenerationJob.queue(character.getId(), CANDIDATES));    // 대기열에 작업 추가
return character;                                                  // AI 를 기다리지 않고 바로 응답
```

같은 기기에서 거의 동시에 두 번 보내면 둘 다 "아직 없음" 검사를 통과할 수 있다.
두 번째는 DB 의 unique 제약에 막혀 `DataIntegrityViolationException` 이 나고, 이를 "이미 만들었어요" 로 바꾼다 (코드 검사 + DB 제약의 이중 방어).

### 다시 만들기: 조건부 UPDATE

```java
@Query("""
    update GuestCharacter c
    set c.regenCount = c.regenCount + 1, c.status = :to, c.updatedAt = :now
    where c.id = :id and c.status = :from and c.regenCount < :maxRegenerations
    """)
int startRegeneration(...);   // 반환값 = 실제로 바뀐 줄 수. 0 이면 거절
```

흔한 방식인 "읽고 → 검사하고 → 쓰기" 는 동시 요청에 약하다.

```java
// ❌ 버튼을 연타하면 두 요청이 모두 ① 에서 0 을 보고 둘 다 ② 를 실행한다
if (character.getRegenCount() < 1) {   // ①
    character.setRegenCount(+1);       // ②
}
```

조건부 UPDATE 는 검사와 변경을 DB 가 한 문장으로 처리한다. 두 번째 요청이 실행될 때는 이미 `regen_count = 1` 이라
WHERE 가 맞지 않아 0 줄이 바뀐다. 후보 선택(`choose`)도 같은 방식이다.

### 응답 DTO

엔티티를 그대로 내보내지 않고 `CharacterResponse`, `MyCharacterResponse` record 로 바꾼다.

- `deviceId` 처럼 밖으로 나가면 안 되는 값이 있다.
- DB 구조가 바뀌어도 API 응답 모양을 유지할 수 있다.
- 상태를 `"choosing"` 처럼 소문자로, 저장소 키를 브라우저가 쓸 주소(`/files/...`)로 바꾼다.

---

## 8. 이미지 생성 대기열: generation/

### 왜 대기열인가

```
요청 처리 중에 바로 AI 호출 ❌            대기열 ✅
하객: 업로드 ─┐                           하객: 업로드 → 즉시 "생성 중" 응답
            │ 30초 대기 (끊기면 실패)      워커: 뒤에서 작업을 꺼내 AI 호출
            ▼ 응답                       하객 화면: 2초마다 상태 확인
```

- 하객 요청을 오래 붙잡지 않는다.
- 동시에 몰려도 작업이 줄을 서서 처리되고, "앞에 3명" 같은 순번을 보여줄 수 있다.
- 실패하면 자동으로 재시도할 수 있다.

### 워커: GenerationWorker

```java
@Scheduled(fixedDelayString = "${app.generation.worker-delay}")   // 이전 실행이 끝나고 2초 뒤 또 실행
public void poll() {
    // ① 짧은 트랜잭션: 작업 하나를 꺼내 RUNNING 표시
    GenerationJob job = tx.execute(status -> jobs.lockNextQueued().map(j -> { j.start(); return j; }).orElse(null));
    if (job == null) return;
    try {
        // ② 트랜잭션 밖: 오래 걸리는 AI 호출
        switch (job.getType()) {
            case CANDIDATES -> processor.generateCandidates(job.getCharacterId());
            case SPRITE -> processor.generateSpriteSheet(job.getCharacterId());
        }
        // ③ 짧은 트랜잭션: 완료 표시
        tx.executeWithoutResult(status -> jobs.findById(job.getId()).ifPresent(GenerationJob::finish));
    } catch (RuntimeException e) {
        // 시도 횟수가 남았으면 다시 QUEUED, 3번 다 실패하면 FAILED + 캐릭터도 FAILED
    }
}
```

**트랜잭션을 셋으로 나눈 이유**: 트랜잭션 안에서 30초짜리 AI 호출을 하면 그동안 DB 연결과 잠금을 계속 붙잡는다.
Supabase 무료 플랜은 연결 수가 적어(설정상 최대 5개) 금방 막힌다. DB 작업만 짧게 끊고 오래 걸리는 일은 밖에서 한다.

### 작업 꺼내기: SKIP LOCKED

```sql
select * from generation_job
where status = 'QUEUED'
order by created_at
limit 1
for update skip locked
```

- `for update`: 꺼낸 줄을 잠근다.
- `skip locked`: 다른 워커가 잠근 줄은 기다리지 않고 건너뛴다.

서버를 여러 대로 늘려도 같은 작업을 두 번 가져가지 않는다. PostgreSQL 로 작업 대기열을 만들 때 쓰는 표준적인 방법이다.

### 멈춘 작업 복구: StaleJobRecovery

워커는 작업을 RUNNING 으로 바꾼 뒤 AI 를 호출한다. 그 사이 서버가 꺼지면(배포, 재시작, 장애) 작업이 RUNNING 으로 남고,
워커는 QUEUED 만 꺼내므로 아무도 다시 처리하지 않는다. 하객 화면은 "생성 중" 에서 영원히 멈춘다.

`StaleJobRecovery` 가 서버 시작 직후 한 번, 그 뒤 1분마다(`stale-check-interval`)
**시작한 지 10분(`stale-after`)이 넘었는데 아직 RUNNING** 인 작업을 찾아 실패 1회로 처리한다.

```java
List<GenerationJob> stale = jobs.lockStaleRunning(startedBefore);   // for update skip locked
for (GenerationJob job : stale) job.recoverStale();                 // = fail("처리 중 서버가 중단되어 다시 시도합니다")
// 재시도 횟수(MAX_ATTEMPTS = 3)가 남았으면 QUEUED → 워커가 다시 처리
// 다 썼으면 FAILED → 캐릭터도 FAILED 로 바꿔 하객 화면이 "실패" 를 보여 준다
```

- **기존 재시도 규칙을 그대로 쓴다.** `start()` 에서 이미 시도 횟수를 올리므로, 처리할 때마다 서버를 죽이는 작업이 있어도 결국 FAILED 로 끝난다(무한 반복 방지).
  최대 시도 횟수는 `GenerationJob.MAX_ATTEMPTS` 한곳에서 워커와 복구가 같이 쓴다.
- **`stale-after` 는 정상 처리 시간보다 넉넉히 길어야 한다.** 짧으면 아직 처리 중인 작업을 다시 대기열에 넣어 같은 작업이 두 번 처리된다(AI 비용도 두 번).
  Gemini 타임아웃이 120초라 10분으로 잡았다. PixelLab 처럼 더 오래 걸리는 단계를 붙이면 다시 확인해야 한다.
- 서버가 여러 대여도 `skip locked` 로 같은 작업을 두 곳에서 복구하지 않는다.
- 실제 DB 에서 "1시간 전에 시작해서 멈춘 작업" 을 넣고 서버를 재시작해 확인했다:
  시도 1회차 → QUEUED 로 복구 후 워커가 다시 처리해 DONE, 시도 3회차 → FAILED + 캐릭터 FAILED.

### 처리기: CharacterGenerationProcessor

```java
List<byte[]> images = candidateGenerator.generateCandidates(storage.get(photoKey), mimeTypeOf(photoKey));  // AI 호출
for (slot ...) storage.put(key, postProcessor.candidate(images.get(slot)), "image/png");               // 후처리 후 저장
tx.executeWithoutResult(status -> {
    candidates.deleteRound(characterId, round);   // 재시도라면 이전에 일부만 저장된 후보 정리
    candidates.saveAll(saved);
    load(characterId).candidatesReady();          // → CHOOSING
});
```

### 생성기: 인터페이스로 갈아끼우기

```java
public interface CandidateGenerator {        // 1단계: 사진 → 후보 4장
    List<byte[]> generateCandidates(byte[] photo, String photoMimeType);
}
public interface SpriteSheetGenerator {      // 2단계: 고른 캐릭터 → 걷기 시트
    byte[] generateSpriteSheet(byte[] character);
}
```

처리기는 인터페이스만 안다. `GenerationConfig` 가 `.env` 값으로 구현체를 골라 끼운다.

```java
@Bean
CandidateGenerator candidateGenerator(AppProperties props, RestClient.Builder restClientBuilder) {
    return switch (props.generation().candidates()) {
        case "fake" -> new FakeImageGenerator();
        case "gemini" -> new GeminiCandidateGenerator(...);
        default -> throw new IllegalStateException("알 수 없는 후보 생성기: " + ...);   // 오타는 서버 시작 시 바로 발견
    };
}
```

| 구현체 | 용도 |
|---|---|
| `FakeImageGenerator` | AI 없이 단색 사람 모양. 개발용, 비용 0. 두 단계 모두 가능 |
| `GeminiCandidateGenerator` | Gemini 로 사진 → 닮은 정면 캐릭터 |
| (예정) PixelLab | 걷기 애니메이션 |

ComfyUI, PixelLab 등으로 바꿀 때 클래스 하나 추가 + 설정값 변경만 하면 되고, 워커, 처리기, API, 프론트는 손대지 않는다.

### Gemini 후보 생성기

- `POST https://generativelanguage.googleapis.com/v1beta/interactions`
- 헤더: `x-goog-api-key`(인증), `Api-Revision: 2026-05-20`(응답 형식을 이 버전으로 고정)
- 본문: `input` 에 프롬프트(text)와 사진(image, base64), `response_format` 으로 PNG 1:1 1K 요청
- 응답: `steps[].content[]` 중 `type: "image"` 의 `data`(base64)를 꺼낸다. 없으면 예외 → 워커가 재시도
- 한 번 호출에 1장이라 4번을 가상 스레드로 동시에 보낸다

```java
List<CompletableFuture<byte[]>> calls = IntStream.range(0, 4)
    .mapToObj(i -> CompletableFuture.supplyAsync(() -> generateOne(...), executor))   // 4개 동시 출발
    .toList();
return calls.stream().map(CompletableFuture::join).toList();                          // 모두 끝날 때까지 대기
```

- 가상 스레드(Java 21): 응답을 기다리는 동안 OS 스레드를 놓아줘서 "외부 API 대기" 작업에 적합하다.
- `executor` 를 생성자로 받는 이유: 테스트에서는 `Runnable::run`(순서대로 실행)을 넣어 결과를 예측 가능하게 한다.
- 프롬프트는 배경을 마젠타(#FF00FF)로 요청한다. 옷이나 머리색에 거의 쓰이지 않아 후처리에서 배경만 지우기 쉽다.
- PNG 로 받는 이유: JPEG 는 압축 얼룩 때문에 배경 제거가 어렵다.

**요금**: 이 프로젝트는 선불 크레딧 방식. Nano Banana 2.1 기준 1K 이미지 1장 약 $0.0336, 후보 4장 약 $0.13.
이미지 모델은 API 무료 사용량이 없고, AI Studio 웹 화면에서는 무료로 프롬프트를 테스트할 수 있다.

### 후처리: ImagePostProcessor

AI 결과(1024px, 마젠타 배경)를 하객 화면이 쓰는 **64x64, 투명 배경, 발이 바닥** 형식으로 맞춘다.

1. **배경 지우기 (flood fill)**: 테두리에서 시작해 배경색과 비슷한(RGB 거리 90 이내) 이웃 픽셀로 번지며 투명하게 만든다.
   테두리와 이어진 영역만 지우므로, 외곽선에 막힌 캐릭터 안쪽(분홍 옷 등)은 남는다.
   배경이 이미 투명이면 색은 비교하지 않는다 (투명 픽셀의 RGB 는 보통 검정이라 어두운 외곽선까지 지워지는 버그가 있었다).
2. **캐릭터 영역만 자르기**: 투명하지 않은 픽셀을 감싸는 최소 사각형.
3. **64px 바닥 정렬**: 하객 화면은 발 위치를 기준점으로 그린다. 비율 유지, 이미 작으면 키우지 않음,
   최근접 이웃으로 줄인다(주변 색을 섞지 않아 도트가 선명하게 유지된다).

---

## 9. 저장소와 오류 처리

**StorageService**: 파일 저장을 인터페이스로 감쌌다. 지금은 `LocalStorageService` 가 `storage/` 폴더에 저장하고 `/files/...` 로 내려준다.
배포할 때 `S3StorageService` 를 만들어 갈아끼운다. 주소는 상대 경로(`/files/...`)라 프론트 개발 서버의 프록시를 그대로 탄다.

**오류 응답**: `ApiException(상태코드, 코드, 메시지)` 를 던지면 `ApiExceptionHandler`(`@RestControllerAdvice`)가 같은 모양으로 응답한다.

```json
{ "code": "NO_REGENERATIONS_LEFT", "message": "다시 만들기는 1번까지 할 수 있어요." }
```

프론트는 `code` 로 분기하고 `message` 를 화면에 그대로 보여 준다.
`server.error.include-stacktrace: never` 로 오류 시 서버 내부 경로가 노출되지 않게 했다.

---

## 10. 테스트

| 테스트 | 확인하는 것 |
|---|---|
| `FakeImageGeneratorTest` | 가짜 생성기가 64px 후보 4장, 4x3 시트를 만드는지 |
| `ImagePostProcessorTest` | 배경 제거, 자르기, 바닥 정렬, 외곽선 안쪽 색 보존, 작은 이미지 확대 금지 |
| `GeminiCandidateGeneratorTest` | 가짜 서버(`MockRestServiceServer`)로 문서대로 요청하는지, 응답을 제대로 해석하는지, 오류·키 누락 처리 |
| `GenerationJobTest` | 실패와 멈춤 복구가 같은 재시도 규칙(최대 3회)을 따르는지 |

`MockRestServiceServer` 는 진짜 서버 대신 요청을 가로채 미리 정한 응답을 돌려준다.
크레딧 없이(비용 0) 연동 코드를 검증할 수 있지만, 문서와 실제 서버가 다를 수 있으니 실제 호출로 한 번은 확인해야 한다.

```bash
./gradlew test
```

---

## 11. 요청 하나를 끝까지 따라가기

| # | 어디서 | 무슨 일이 |
|---|---|---|
| 1 | 브라우저 | 링크 접속 → 배경을 그리고 `GET /api/characters` |
| 2 | `DeviceIdFilter` | 쿠키가 없으니 `wg_device` 발급 |
| 3 | `CharacterController.list` | 완성된 캐릭터 목록 + `syncCursor` → 정원에 하객들이 걸어 다님 |
| 4 | 프론트 만들기 창 | `GET /me` → `NO_CHARACTER` → 입력 화면 |
| 5 | 프론트 | 사진을 1024px JPEG 로 줄여서 `POST /api/characters` |
| 6 | `CharacterService.create` | 규칙 검사 → 사진 저장 → `PENDING_CANDIDATES` 저장 → 대기열에 CANDIDATES → 바로 응답 |
| 7 | 프론트 | "픽셀 옷 입는 중..." , 2초마다 `GET /me` |
| 8 | `GenerationWorker` | `SKIP LOCKED` 로 작업을 꺼내 RUNNING |
| 9 | `CharacterGenerationProcessor` | 생성기 → 후보 4장 → 후처리 → 저장 → `CHOOSING` |
| 10 | 프론트 | 후보 4개 선택 화면 (다시 만들기 시 조건부 UPDATE 후 8~9 반복) |
| 11 | `CharacterService.choose` | 조건부 UPDATE 로 `PENDING_SPRITE` → 대기열에 SPRITE |
| 12 | 워커 + 처리기 | 걷기 시트 생성 → 저장 → `COMPLETE` |
| 13 | 프론트 | "입장했어요!" → 목록 갱신 → 걷기 시작 + 말풍선 |
| 14 | 다른 하객 폰 | 10초 안에 since 폴링으로 받아 정원에 등장 |

---

## 12. 현재 상태와 알려진 약점

**동작하는 것**: fake 생성기로 캐릭터 만들기 전체 흐름, 기기당 제한, 정원 표시.

| 항목 | 상태 |
|---|---|
| Gemini 실제 호출 | 코드·테스트 완료. 크레딧 충전 후 실제 확인 필요 |
| 걷기 애니메이션 (PixelLab) | 미착수. 지금은 fake 시트라 고른 캐릭터와 모양이 다름 |
| 관리자 API | 없음 |
| 배포 (Docker, AWS), 저장소 (S3) | 파일만 있음 / 로컬 폴더 |

**알려진 약점**

- 사진은 `storage.put` 후 DB 저장이 실패하면 파일만 남는다 (정리 작업 없음).
- (해결됨) 워커가 AI 호출 중에 서버가 꺼지면 작업이 `RUNNING` 으로 남던 문제 → `StaleJobRecovery` (8장)

---

## 13. 실행 방법

이 PC 의 기본값은 Java 8 / Node 14 (회사 프로젝트용) 이므로 이 프로젝트는 버전을 지정해서 실행한다.

1. `.env.example` 을 `.env` 로 복사해 DB 접속 정보를 채운다 (Supabase > Connect > JDBC > Session pooler).
2. IntelliJ 에서 Project SDK 를 Java 21(`C:\Program Files\Java\jdk-21`)로 지정하고 `WeddingBackendApplication` 실행.
   또는:

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21" ./gradlew bootRun
```

이미지 생성기는 `.env` 의 `CANDIDATE_GENERATOR` (`fake` | `gemini`), `SPRITE_GENERATOR` (`fake`) 로 고른다.

---

## 14. 공부 순서와 확인 질문

1. `CharacterStatus.java`, `V1__init.sql` — 데이터 모양과 단계
2. `CharacterController` → `CharacterService` — 요청과 규칙, 특히 조건부 UPDATE
3. `GenerationWorker` → `CharacterGenerationProcessor` — 대기열과 트랜잭션 분리
4. `GenerationConfig`, `GeminiCandidateGenerator`, `ImagePostProcessor` — 생성기 교체 구조와 후처리

스스로 답해 보기:

- 재생성 버튼을 두 번 빠르게 누르면 서버에서 정확히 무슨 일이 일어나는가?
- 워커가 AI 호출 도중 서버가 꺼지면 그 작업은 어떻게 되는가? `stale-after` 를 1분으로 줄이면 어떤 문제가 생기는가? (8장 참고)
- 하객 화면이 처음엔 전체를, 다음부터는 변경분만 받는 건 어떤 값 덕분인가?
- `GenerationConfig` 에서 `default` 분기가 예외를 던지면 어떤 점이 좋은가?
