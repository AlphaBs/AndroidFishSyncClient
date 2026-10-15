# .NET과 Kotlin core의 현재 차이

## 비교 범위

- 갱신일: 2026-10-15.
- 원본: AlphaBs/FishSyncClient의 `src`, 커밋 `f7dad08cf62ca23c1feec44bafe576c4da1b2bce`.
- Kotlin: 현재 `fishsync-core/src/main/kotlin` 구현. `storage` 어댑터를 포함한다.
- 서버 클라이언트, Android 어댑터와 샘플 UI는 비교 범위에서 제외한다.
- 소스 검토에 따른 비교다. 재현·대조 테스트가 없는 항목은 아래에 따로 표시한다.

타입별 대응은 [타입 대응표](upstream-type-map.md), 책임 구분은 [core 구조](core-architecture.md), Android 저장소 경계는 [Android 대응 경계](android-adaptations.md)를 참고한다.

## 공통 동작

| 항목 | 계약 |
| --- | --- |
| 규칙 선택 | 경로와 실행 조건이 모두 맞는 첫 번째 규칙을 선택한다. |
| FullSync | 추가·갱신을 수행하고 대상에만 있는 파일을 삭제 후보로 반환한다. |
| UpdateOnly | 추가·갱신을 수행하고 대상에만 있는 파일은 보존한다. |
| InstallOnly | 누락 파일만 추가하고 기존 대상은 비교·갱신하지 않는다. |
| Exclude | 비교·전송·삭제에서 제외한다. |
| 실행 조건 | Always 또는 OnNewVersion을 사용하며 강제 실행도 새 버전 조건을 충족한다. |
| 전송·검증 | 대상에 직접 쓰고 전송에 사용한 규칙의 비교기로 다시 검사한다. 불충족 시 FileIntegrityException을 전달한다. |
| 체크섬 생략 | LocalFileChecksumComparer는 대상이 존재하면 내용을 읽지 않고 true를 반환한다. 바이트 동일성을 보장하는 비교기가 아니다. |
| 삭제 | 삭제 후보 반환과 실제 삭제를 분리한다. 호출자가 삭제 메서드를 명시적으로 실행한다. |
| 버전 | UTF-8 128바이트 제한, trim, 파일 직접 쓰기와 NullVersionManager의 항상 새 버전 판단을 유지한다. |
| 진행률 누적 | 스레드별 ThreadLocal 값에 누적한 뒤 전체 값을 합산한다. |

## 허용하는 런타임·API 차이

### glob과 문자열 비교

Kotlin은 내부 [GlobPattern](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/internals/GlobPattern.kt)을 사용한다. `*`·`?`는 경로 요소 안에서, 독립된 `**`는 디렉터리 0개 이상과 일치한다. `[a-z]`·중괄호는 일반 문자로 처리한다. DotNet.Glob의 전체 문법과 특이 동작은 지원하지 않는다. 재귀·백트래킹과 외부 matcher 의존성은 없다.

경로 대소문자 비교에는 Kotlin 표준 String.CASE_INSENSITIVE_ORDER와 ignoreCase를 사용한다. .NET OrdinalIgnoreCase와 Unicode 판정이 다를 수 있다. 예를 들어 Kotlin은 `I`/`ı`, `İ`/`i`도 대소문자를 무시하면 같은 이름으로 취급한다.

버전 문자열 trim에는 [DotNetWhitespace](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/internals/DotNetWhitespace.kt)를 사용해 원본의 공백 집합을 유지한다.

### 스트림과 취소

.NET의 Stream은 JVM의 InputStream·OutputStream으로 나뉜다. 전송·해시 계산은 버퍼 작업 사이에서 코루틴 취소를 확인한다. blocking I/O가 반환될 때까지 취소가 늦어질 수 있다. 전송 중 강제 인터럽트나 스트림 자동 닫기는 사용하지 않는다.

[LocalSyncFile](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/LocalSyncFile.kt)은 [StreamOpenHelper](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/internals/StreamOpenHelper.kt)로 IO dispatcher에서 스트림을 연다. 열기 후 호출자에게 반환되기 전에 취소·예외가 발생하면 아직 전달하지 못한 스트림을 닫고 원래 예외를 전달한다. 닫기 오류는 suppressed로 보존한다. 정상 반환한 스트림의 정리는 호출자가 담당한다.

원본의 로컬 스트림 열기는 동기적으로 완료된 ValueTask를 반환하므로 같은 dispatcher 복귀 경계가 없다. Kotlin의 자원 전달 경계를 보완하는 처리이며 전송 중 강제 취소 정책과는 별개다.

### 진행률 저장소

JVM ThreadLocal에는 .NET의 Values가 없으므로 등록한 슬롯 목록을 직접 합산한다. 종료된 스레드의 누적값도 보존한다. 전체 슬롯을 같은 시점의 원자적 스냅샷으로 읽지는 않는다.

clear는 새 저장소로 교체한다. close는 저장소·슬롯 목록·현재 스레드의 연결을 해제한다. close 후 report·aggregateProgress는 거부하며 clear로 다시 시작할 수 있다. 다른 스레드의 ThreadLocal 엔트리 정리 시점은 JVM에 의존한다. clear·close와 보고를 동시에 실행할 때 강한 원자적 경계를 보장하지 않는다.

### 컬렉션 조회와 동등성

현재 사용 방식에서는 다음 차이를 허용한다.

- .NET GetItems는 Dictionary.Values 뷰를 반환하고 Kotlin getItems는 toList로 목록을 복사한다. 조회 후 추가한 파일은 기존 Kotlin 목록에 나타나지 않는다. 항목 객체는 공유하므로 기존 항목의 진행률 변경은 보인다.
- Kotlin iterator도 목록을 생성하므로 조회 비용과 수정 중 열거 동작이 다르다.
- FileProgressEvent·SyncFileByteProgress·SyncFileProgressItem은 원본의 일반 클래스와 달리 Kotlin data class이므로 값 기반 equals·hashCode를 사용한다. 동일한 값을 가진 별도 객체가 같다고 판단될 수 있다.
- 진행률이 변하는 SyncFileProgressItem을 HashSet의 항목이나 Map의 키로 사용하면 hashCode 변경에 주의해야 한다.

### enum과 예외

| 항목 | .NET 값 | Kotlin ordinal |
| --- | --- | --- |
| SyncAction.FullSync | 1 | 0 |
| SyncAction.InstallOnly | 2 | 2 |
| SyncAction.Exclude | 3 | 3 |
| SyncAction.UpdateOnly | 4 | 1 |
| SyncCondition.Always | 1 | 0 |
| SyncCondition.OnNewVersion | 2 | 1 |

현재 규칙 로직은 이름·타입으로 비교한다. ordinal을 원본 숫자로 저장·전송하려면 명시적인 변환이 필요하다.

예외는 IllegalArgumentException·IllegalStateException·NoSuchElementException 등 JVM 타입으로 대응한다. 원본 예외 타입과 메시지까지 같은 계약은 아니다.

## 추가 확인이 필요한 동작 차이

### 규칙 패턴 전처리와 빈 패턴

원본은 패턴의 구분자만 치환하지만 Kotlin [SyncRuleEvaluator](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncRuleEvaluator.kt)는 패턴에도 normalizePath를 적용한다.

| 입력 | 원본 | Kotlin 소스에서 도출한 동작 |
| --- | --- | --- |
| 패턴 `mods//./+*`, 경로 `mods/+example.jar` | 불일치 | 패턴을 `mods/+*`로 정규화하므로 일치 |
| 패턴 `../outside/**` | 파싱 후 `outside/file`과 불일치 | 부모 경로 요소를 거부해 생성 단계에서 예외 |
| 빈 패턴 | 파일 탐색·전송 전에 거부 | 명시적 검증이 없어 파일 유무에 따라 나중에 실패하거나 비교 성공 가능 |

패턴 전처리와 빈 패턴의 오류 시점은 정책 결정 및 대조 테스트가 필요하다. Kotlin enum은 일반 호출에서 잘못된 숫자 값을 만들 수 없으므로 원본의 enum 숫자 범위 검사와 같은 검사는 필요하지 않다.

### 병렬 실패 처리

원본은 Dataflow ActionBlock, Kotlin은 coroutineScope 안에서 파일별 launch와 Semaphore를 사용한다. 기본 병렬도는 양쪽 모두 CPU 수에 따라 1~4개다. Kotlin은 동시 실행 수를 제한하지만 입력 수만큼 대기 코루틴이 만들어질 수 있다. 원본도 대기열 용량을 제한하지 않는다.

Kotlin에서 작업 하나가 실패하면 다른 자식 작업도 취소된다. 실패 시 다른 파일의 완료·중단 결과와 예외 우선순위가 원본과 같은지는 실패 시점을 통제한 대조 테스트가 필요하다. 두 구현 모두 병렬 결과 순서가 같다고 보장하지 않는다.

### HTTP 스트림 설정

[ReadableHttpSyncFile](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/ReadableHttpSyncFile.kt)은 주입된 OkHttpClient의 timeout 설정을 따른다. 원본은 반환 스트림이 CanTimeout을 지원하면 ReadTimeout을 10초로 설정한다. 보장하는 설정 범위가 다르며 HTTP 예외 타입·메시지도 다르다.

Content-Length와 등록된 메타데이터 크기의 차이를 진행률에 반영하고, 스트림을 닫을 때 응답을 정리하는 역할은 유지한다.

## 코드 구조 차이

| 항목 | Kotlin 구현 |
| --- | --- |
| 인터페이스 | I 접두사를 사용하지 않는다. 기본 구현은 Default 또는 구체적인 역할로 구분한다. |
| 파일·패키지 | 주된 타입별 파일과 files·filecomparers·syncer·versions·progress·internals 패키지를 사용한다. |
| 비동기 API | Task·ValueTask·CancellationToken 대신 suspend와 호출 코루틴 문맥을 사용한다. |
| 진행률 API | IProgress 대신 타입별 콜백과 report 메서드를 사용한다. |
| 값 객체 | struct·record 대신 클래스·data class를 사용한다. |
| 메타데이터 | init 속성 대신 생성자 val로 초기화한다. |
| 경로 인덱스 | 해시 Dictionary 대신 정렬 Map을 사용한다. 원본 입력 순서를 유지하려고 입력 목록도 보관한다. |
| 로컬 파일 경계 | exists·length를 재정의할 수 있어 경로가 없는 저장소도 같은 비교기에 연결한다. |
| 저장소 어댑터 | 원본에 없는 storage 패키지가 일반 파일·SAF 접근을 연결한다. Android URI 타입은 core에 넣지 않는다. |

SyncPathComparer의 인덱스 생성은 Kotlin에서 대체로 O(n log n), 원본 해시 인덱스에서는 평균 O(n)이다. 실제 성능 차이를 측정한 결과는 없다.

StorageSyncer는 원본 SyncFileCollectionSyncer를 상속하며 대상 목록과 파일 쌍 생성·명시적 삭제만 저장소에 연결한다. 별도 전송 알고리즘이나 적용 정책을 두지 않는다.

## 검증과 남은 확인

테스트 실행은 CI에 위임한다. 스트림 반환 중 취소에 대해서는 StreamOpenHelperTest에 읽기·쓰기 정리, 정상 반환 시 소유권 유지, 열기 오류 전달, 정리 오류의 suppressed 보존 테스트를 둔다. CI 성공 여부는 해당 변경의 실행 결과로 확인한다.

원본 대조 fixture가 사용하는 커밋은 workflow에 지정된 참조를 기준으로 한다. 이 문서의 원본 조사 커밋과 CI 참조를 같은 식별자로 취급하지 않는다. CI 통과가 모든 입력과 실패 상황에서 원본과 동일한 동작을 보장하지는 않는다.

남은 확인 항목은 빈 패턴 검증 시점, 패턴 정규화 정책, 병렬 실패 시 적용 결과·예외 전파, HTTP timeout 보장 범위다.
