-- 방명록 (부부 1쌍 = 1행). 관리자 화면의 "부부 설정"
create table guestbook (
    id              bigserial    primary key,
    groom_name      varchar(30)  not null,
    bride_name      varchar(30)  not null,
    wedding_at      timestamptz,
    status          varchar(20)  not null default 'OPEN', -- OPEN | READ_ONLY (행사 후)
    max_characters  int          not null default 300,    -- 이미지 생성 비용 상한
    created_at      timestamptz  not null default now()
);

-- 하객 캐릭터. 기기(쿠키)당 1개: (guestbook_id, device_id) unique
create table guest_character (
    id              uuid         primary key,
    guestbook_id    bigint       not null references guestbook (id),
    device_id       varchar(36)  not null,
    name            varchar(20)  not null,
    dialogue        varchar(100) not null,
    status          varchar(30)  not null, -- PENDING_CANDIDATES | CHOOSING | PENDING_SPRITE | COMPLETE | FAILED
    regen_count     int          not null default 0,
    photo_key       varchar(255),          -- 업로드한 원본 사진 (저장소 키)
    image_key       varchar(255),          -- 선택한 정지 이미지
    sprite_key      varchar(255),          -- 4x3 걷기 스프라이트시트
    sprite_columns  smallint,
    sprite_rows     smallint,
    hidden          boolean      not null default false, -- 관리자가 숨긴 캐릭터
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    unique (guestbook_id, device_id)
);
-- 폴링: "since 이후 바뀐 캐릭터"
create index guest_character_sync_idx on guest_character (guestbook_id, updated_at);

-- 후보 이미지 (최초 4장 + 재생성 4장)
create table character_candidate (
    id              bigserial    primary key,
    character_id    uuid         not null references guest_character (id) on delete cascade,
    round           smallint     not null, -- 0: 최초, 1: 재생성
    slot            smallint     not null, -- 0~3
    image_key       varchar(255) not null,
    created_at      timestamptz  not null default now(),
    unique (character_id, round, slot)
);

-- 이미지 생성 대기열. 워커가 queued 를 하나씩 가져가 처리한다
create table generation_job (
    id              bigserial    primary key,
    character_id    uuid         not null references guest_character (id) on delete cascade,
    type            varchar(20)  not null, -- CANDIDATES | SPRITE
    status          varchar(20)  not null default 'QUEUED', -- QUEUED | RUNNING | DONE | FAILED
    attempts        int          not null default 0,
    error           text,
    created_at      timestamptz  not null default now(),
    started_at      timestamptz,
    finished_at     timestamptz
);
create index generation_job_queue_idx on generation_job (status, created_at);
