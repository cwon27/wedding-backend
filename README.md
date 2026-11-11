# wedding-backend

픽셀 방명록 백엔드 (Spring Boot 4, Java 21, Gradle). 프론트엔드는 `wedding-frontend` 레포.

## 구조

```
src/main/java/com/wedding/guestbook/
├─ character/    하객 캐릭터 API, 폴링(since 커서)
├─ guestbook/    부부 설정 (방명록 1개)
├─ device/       로그인 없이 기기 구분 (HttpOnly 쿠키, @DeviceId)
├─ generation/   이미지 생성 대기열 + 워커, 단계별 생성기(CandidateGenerator, SpriteSheetGenerator), 후처리
├─ storage/      이미지 저장소 (로컬 → S3)
└─ config/       설정, CORS
src/main/resources/db/migration/   Flyway 테이블 정의
```

## 로컬 실행

1. Supabase 프로젝트를 만들고 `.env.example` 을 `.env` 로 복사해 DB 접속 정보를 채운다.
   (Project Settings > Database > Connection string > JDBC > **Session pooler**)
2. Java 21 로 실행한다. 처음 실행하면 Flyway 가 테이블을 만든다.

```bash
./gradlew bootRun
```

IntelliJ 에서는 Project SDK 를 21 로 지정하고 `WeddingBackendApplication` 을 실행한다.

## 이미지 생성기 바꾸기

캐릭터는 두 단계로 만든다. 단계마다 `.env` 값으로 생성기를 고른다.

| 단계 | 설정 | 값 |
|---|---|---|
| 1. 사진 → 정면 캐릭터 후보 4장 | `CANDIDATE_GENERATOR` | `fake` (더미 도트, 비용 0) / `gemini` (`GEMINI_API_KEY` 필요) |
| 2. 고른 캐릭터 → 걷기 스프라이트시트 | `SPRITE_GENERATOR` | `fake` / `pixellab` (예정) |

어떤 생성기를 쓰든 결과는 `ImagePostProcessor` 가 배경 제거, 64px 맞춤으로 같은 형식으로 만든다.
