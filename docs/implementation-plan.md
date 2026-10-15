# AndroidFishSyncClient 구현 계획

작성일: 2026-10-15 (Asia/Seoul)

## 1. 목표와 확정된 범위

FishSyncClient의 `src` 동작을 Kotlin으로 포팅하고 Android 앱에서 FishSyncServer와 파일을 동기화할 수 있는 라이브러리를 만든다.

첫 버전의 범위:

- 파일 목록 비교와 추가·수정·삭제·동일 파일 분류
- 파일 크기와 MD5/SHA1 체크섬 비교
- 순서가 있는 glob 규칙과 버전 조건
- 로컬 복사, 서버 다운로드·업로드, 삭제 처리
- 서버 로그인, 버킷 목록·정보·파일 목록 조회, PULL/PUSH
- 진행률, 제한된 병렬 처리, 취소 및 부분 실패 결과
- 앱 전용 폴더와 SAF로 선택한 폴더 지원
- 시스템 폴더 선택기를 사용하는 테스트용 Android 앱

PULL은 서버 상태를 기준으로 로컬을 맞추고, PUSH는 로컬 상태를 기준으로 서버를 맞춘다. 자동 충돌 병합이나 양방향 동기화는 첫 버전의 기능이 아니다.

## 2. 조사 기준

- 원본: https://github.com/AlphaBs/FishSyncClient
- 포팅 기준 커밋: `79840441f8398a0934573664e58f07d9f1fe1caf`
- 상세 코드 조사에 사용한 로컬 커밋: `f7dad08cf62ca23c1feec44bafe576c4da1b2bce`
- 위 두 커밋 사이에는 `src` 변경이 없음을 확인했다.
- 서버 연동 조사: FishSyncServer의 `FishBucket`, `FishBucket.ApiClient`, `FishBucket.SyncClient`
- 서버 로컬 조사 기준: `2d97de994e6892ab0d3032084453275829b59b4f`

`src`만으로는 서버 로그인과 PUSH를 구현할 수 없다. 서버 DTO 및 HTTP 작업 처리 계약까지 Kotlin으로 구현해야 한다. 구현 착수 시 실제 지원할 서버 버전을 고정하고, 기존 CLI가 사용하는 NuGet 버전과 현재 서버 계약의 차이를 기록한다. 운영 서버에 쓰기 요청을 보내지 않고 계약 테스트부터 수행한다.

## 3. 모듈과 기술 선택

| 모듈 | 책임 | 산출물 |
| --- | --- | --- |
| `fishsync-core` | 상대 경로, 메타데이터, 규칙, 비교, 동기화 실행과 저장소 인터페이스 | Kotlin/JVM JAR |
| `fishsync-client` | 서버 API, DTO 직렬화, HTTP 파일 전송, PULL/PUSH 조율 | Kotlin/JVM JAR |
| `fishsync-android` | 앱 전용 폴더 연결, SAF 어댑터, URI 권한 관리 지원 | Android AAR |
| `sample-app` | 라이브러리 사용과 기능 검증 | 테스트 APK |

의존성 방향은 `client → core`, `android → client/core`, `sample-app → android`로 제한한다. 코어에 `Context`, `Uri`, UI 컴포넌트를 넣지 않는다.

기본안은 Kotlin, Coroutines, OkHttp, kotlinx.serialization이다. Android 최소 버전은 API 26을 제안하며 첫 빌드 구성에서 확정한다. Kotlin·Gradle·AGP·JDK·라이브러리 버전은 호환 조합을 확인한 뒤 고정한다. JNI와 NDK는 초기 구현에 필요하지 않다.

UI와 토큰 영구 저장은 사용하는 앱이 담당한다. 라이브러리는 토큰 공급자를 주입받으며 비밀번호나 토큰을 로그에 출력하지 않는다. 샘플 앱의 첫 버전에서는 로그인 정보를 메모리에 유지한다.

## 4. 원본 동작 호환성

| 원본 영역 | Kotlin 구현에서 유지할 계약 |
| --- | --- |
| `RootedPath`, `PathOptions`, `LocalPathGuard` | 상대 경로 정규화, 중복 및 루트 이탈 검사, 링크 처리 정책 |
| `Files` | 메타데이터, 읽기·쓰기 스트림, HTTP 응답 수명과 자원 해제 |
| `FileComparers` | 크기·체크섬 비교, 누락된 메타데이터와 잘못된 메타데이터의 구분 |
| `SyncRuleEvaluator` | 순서대로 평가하여 패턴과 조건이 처음 일치한 규칙 적용 |
| `SyncAction` | FullSync, UpdateOnly, InstallOnly, Exclude |
| `SyncCondition`, `SyncContext` | Always, OnNewVersion, IsNewVersion, IsForced |
| `ParallelSyncFilePairSyncer` | 제한된 동시 실행, 실패 전파, 전송 후 검증 |
| `Progress` | 파일·바이트 진행률 및 크기 미확정 전송 처리 |
| `FileVersionManager` | UTF-8 128바이트 한도, trim 및 버전 비교 |

`FullSync`는 추가·갱신 및 대상에만 있는 파일의 삭제 후보를 만든다. `UpdateOnly`는 추가·갱신하되 대상 전용 파일을 보존한다. `InstallOnly`는 없는 파일만 추가하고 기존 파일을 보존한다. `Exclude`는 비교·전송·삭제에서 제외한다. 규칙이 누락되거나 어떤 경로에도 적용 규칙이 없으면 명확한 오류로 처리한다.

glob은 사용자 결정에 따라 내부 GlobPattern으로 구현한다. `*`, `?`, `**`, 구분자와 대소문자 옵션을 검증하며 DotNet.Glob의 문자 집합·잘못된 패턴 동작은 복제하지 않는다. 알고리즘 이름 `md5`/`sha1`은 원본 계약을 따르고, 체크섬 hex의 대소문자는 원본처럼 비교한다. 체크섬이 생략된 경우의 원본 동작도 테스트로 고정한다.

안전한 파일 적용 등 원본과 다른 동작은 의도한 변경으로 문서화한다. .NET 타입과 메서드 이름을 그대로 복제하는 것보다 동작 호환성과 Kotlin API 사용성을 우선한다.

## 5. 저장소와 SAF 폴더 선택

코어는 상대 경로와 저장소 인터페이스를 사용한다. 인터페이스에는 파일 탐색, 메타데이터 조회, 스트림 열기, 디렉터리 생성, 임시 파일 생성·적용 및 삭제 기능을 둔다. 구체적인 메서드는 비교·전송 테스트를 먼저 작성하면서 확정한다.

### 일반 파일 저장소

- 앱이 전달한 신뢰 루트를 정규화하고 그 내부 경로만 허용한다.
- 루트 내부의 심볼릭 링크 및 루트 이탈을 검사한다.
- Android의 `/sdcard`, `/data/data` 등 시스템 경로 별칭을 원본의 모든 조상 링크 거부 로직과 구분한다.
- 읽기·쓰기·삭제 직전에도 경로를 검증한다. 사전 검사만으로 모든 경합을 차단한다고 주장하지 않는다.

### SAF 저장소

- 샘플 앱에서 `ActivityResultContracts.OpenDocumentTree`로 폴더를 선택한다.
- 반환된 읽기·쓰기 권한 범위 안에서 persistable URI 권한을 저장한다.
- 트리 URI를 실제 파일 경로로 변환하지 않는다. `ContentResolver`와 문서 API로 접근한다.
- 파일 크기 미상, 접근 권한 취소, 폴더 삭제, 제공자 장애를 처리한다.
- 문서 ID와 표시 이름을 구분하고, 경로 구분자·중복 이름·이름 변형을 검증한다.
- 큰 파일 목록에서는 문서 조회 횟수를 측정하고 재귀 탐색 비용을 제한한다.
- rename, 교체, 삭제 및 임시 파일 지원을 저장소 능력으로 구분한다.
- 제공자가 원자적 교체를 지원하지 않으면 보장 수준을 알리고 안전한 적용 방식이 없는 작업은 실패시킨다.

Android가 선택을 제한하는 `Android/data`, `Android/obb` 및 일부 루트 위치를 지원한다고 약속하지 않는다. 다른 앱으로 설치된 Amethyst의 전용 폴더에 자유롭게 접근할 수 없다. 추후 Minecraft 런처와 결합할 때는 같은 앱 내부 저장소를 사용하거나 별도의 공유 방식을 설계한다.

## 6. 파일 적용과 실패 처리

PULL은 비교 → 임시 파일 다운로드 → 검증 → 적용 → 삭제 순서로 수행한다. 임시 파일은 선택한 저장소의 같은 볼륨 또는 동일 제공자 안에 만들고 동기화 대상 목록에서 제외한다.

다운로드 실패·취소·체크섬 오류가 발생하면 기존 대상 파일을 교체하지 않고 삭제 단계에 진입하지 않는다. 전송·적용 중 로컬 또는 서버가 변경됐을 때는 재비교하거나 변경 충돌을 반환한다. 파일별 안전한 교체를 목표로 하며 전체 폴더에 대한 원자적 트랜잭션은 보장하지 않는다. 이미 적용한 파일과 실패한 파일을 결과에 구분한다.

일반 파일과 SAF 모두 스트리밍으로 처리해 파일 크기에 비례해 메모리를 사용하지 않는다. 취소는 Coroutines에서 HTTP 호출과 스트림 작업으로 전달하며 `CancellationException`을 일반 오류로 삼키지 않는다. 같은 루트에 대한 동시 동기화를 막는다.

PUSH는 비교 시점의 파일 정보를 검증한 전송용 복사본 또는 동등한 안정된 입력을 사용한다. 전송 중 파일이 변경되면 다음 동기화 대상으로 남기거나 충돌로 반환한다. 업로드 실패 시 서버에 일부 작업이 반영될 수 있음을 결과에 표시한다.

## 7. 서버 프로토콜

조사한 API는 `/auth/login`, `/buckets`, `/buckets/common/{id}`, `/buckets/common/{id}/files`, `/buckets/common/{id}/sync`다. API base URL의 `/api` 접두사가 유실되지 않도록 URL 결합을 테스트한다.

- 로그인 응답 토큰 및 Bearer 인증
- 버킷 목록·정보·파일 메타데이터와 다운로드 URL
- 동기화 요청의 상대 경로·크기·MD5 표현
- HTTP 작업 타입은 대소문자까지 정확히 `Http`
- 작업 매개변수 `method`, `url`, `headers.*`
- `Content-MD5`의 Base64 표현과 콘텐츠 헤더 처리
- 검증 실패·중복 경로·알 수 없는 작업·읽기 전용·한도 오류

API 인증 토큰은 서버가 반환한 외부 전송 URL로 자동 전달하지 않는다. 전송 작업이 명시한 헤더를 사용하며 서명된 URL을 문자열 재작성으로 손상시키지 않는다.

PUSH는 서버 비교/동기화 요청 → 필요한 HTTP 작업 수행 → 다시 서버에 요청 → 성공 확인 흐름을 사용한다. 재시도 상한, 만료된 전송 URL 재발급, 일시적 오류와 영구 오류의 구분을 구현한다. 비멱등 작업을 무조건 재전송하지 않는다. 성공 후 목록 새로고침 실패는 업로드 실패와 구분한다.

## 8. 공개 API 방향

서버 연결과 인증은 `FishSyncClient`, 읽기 대상은 `ReadableSyncStorage`, 변경 대상은 `WritableSyncStorage`로 전달한다. 저장소 비교는 원본 `SyncFileCollectionComparerResult`, 실행은 `SyncResult`, 진행률은 `Flow<SyncProgress>`로 제공한다. 클라이언트의 PULL/PUSH는 사전 비교나 승인 없이 실행한다.

```kotlin
val storage = SafSyncStorage(context, treeUri)
val client = FishSyncClient(serverUrl, tokenProvider)
val result = client.pull(bucketId, storage, rules)
val pushResult = client.push(bucketId, storage, scratchDirectory)
```

진행 이벤트를 수집하지 않아도 동기화가 교착되지 않게 한다.

## 9. 샘플 앱 흐름

1. 서버 URL 및 로그인 정보 입력
2. 버킷 목록 조회·선택
3. 앱 전용 폴더 또는 시스템 선택기로 대상 폴더 지정
4. PULL/PUSH 방향 및 규칙 확인
5. 동기화 실행
6. 파일·바이트 진행률 표시와 취소
7. 성공·실패·취소 및 일부 적용 결과 표시

폴더 URI는 재사용하되 권한 유효성을 매번 확인한다. 기본 삭제 정책을 화면에 명시한다. 화면 회전으로 작업을 중복 실행하지 않게 한다. 프로세스 종료 후 이어받기는 첫 버전에서 보장하지 않는다.

## 10. 구현 단계와 완료 조건

| 단계 | 작업 | 완료 조건 |
| --- | --- | --- |
| 1 | 계약 고정, Gradle 모듈·테스트 구성 | 원본 기준과 JSON fixture, 빌드 버전, 공개 API 초안 준비 |
| 2 | 경로·메타데이터·체크섬·규칙·비교 | .NET과 Kotlin의 비교 결과가 공통 사례에서 일치 |
| 3 | 일반 파일·SAF 어댑터, 폴더 선택 샘플 | 폴더 선택·재접근·탐색·읽기·쓰기·삭제·권한 취소 검증 |
| 4 | 로컬 전송·안전한 적용·진행률·취소 | 중단·검증 실패·공간 부족에서 기존 파일 보호 |
| 5 | 로그인·버킷 API·PULL | 모의 서버와 지정 테스트 버킷에서 PULL 성공 |
| 6 | PUSH와 서버 작업 처리 | 추가·수정·삭제·반복 sync·부분 실패 검증 |
| 7 | 샘플 앱 전체 흐름, AAR·문서·CI | Android 앱에서 폴더 선택부터 PULL/PUSH 및 취소까지 재현 가능 |

## 11. 검증 전략

- 원본 `test`의 규칙, 체크섬 계약, 경로 안전성, 버전, 진행률 및 병렬 실패 사례를 우선 이식한다.
- 동일 fixture에 대해 .NET과 Kotlin 결과를 비교한다. 결과 컬렉션의 비결정적 순서는 정규화한다.
- HTTP 테스트는 모의 서버로 JSON, 헤더, 만료 URL, 지연, 중단, 오류와 취소를 검증한다.
- Android 계측 테스트는 앱 전용 폴더와 SAF 테스트 문서 제공자를 사용한다.
- 시스템 폴더 선택기, 권한 유지, 긴 경로·한글 파일명·빈 파일·대용량 파일을 에뮬레이터에서 검증한다.
- 최저 지원 API와 최신 안정 API를 검증하고 실제 단말에서도 최소 한 번 SAF·전송을 확인한다.
- 실제 서버 쓰기 테스트는 운영 버킷과 분리된 테스트 버킷에서만 진행한다.
- R8 적용 샘플 앱에서 직렬화와 공개 API를 검증한다.

출시 기준은 빌드 성공뿐 아니라 원본 계약 테스트, 저장소 실패 보호, 서버 프로토콜 호환성 및 Android 샘플 앱 검증의 통과다.

## 12. 배포와 후속 범위

처음에는 Gradle 프로젝트 의존성 또는 Maven Local로 샘플 앱에서 사용한다. 배포 시 AAR와 전이 의존성 메타데이터, 소스 및 문서를 함께 제공한다. 공개 Maven 배포 위치는 첫 릴리스 전에 결정한다. 포팅한 원본 코드와 의존성의 라이선스 고지를 유지한다.

후속 범위는 WorkManager/foreground service 연동, 예약 동기화, 파일 감시, 전송 재개, 추가 문서 제공자 최적화 및 Minecraft 런처 통합이다. Kotlin Multiplatform과 자동 충돌 병합은 별도 요구가 있을 때 검토한다.

## 참고

- [FishSyncClient](https://github.com/AlphaBs/FishSyncClient)
- [FishSyncServer](https://github.com/AlphaBs/FishSyncServer)
- [Android 앱 전용 저장소](https://developer.android.com/training/data-storage/app-specific)
- [Android SAF 문서 및 폴더 접근](https://developer.android.com/training/data-storage/shared/documents-files)
- [Android 라이브러리 배포](https://developer.android.com/build/publish-library)
