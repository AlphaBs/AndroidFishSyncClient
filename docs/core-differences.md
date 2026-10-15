# .NET과 Kotlin core 차이 분석

## 비교 범위와 기준

- 조사일: 2026-10-15.
- 원본: AlphaBs/FishSyncClient의 `src`, 커밋 `f7dad08cf62ca23c1feec44bafe576c4da1b2bce`.
- Kotlin: `fishsync-core/src/main/kotlin`, 커밋 `0ec116ebe2a107b63fd3d4468906cad5f48fe54e`.
- Kotlin core에 포함된 `storage` 어댑터도 비교 범위에 포함한다. 서버 클라이언트, Android 어댑터와 샘플 UI 자체는 비교 범위에서 제외한다.
- 소스와 기존 테스트를 읽은 정적 분석이다. 이 문서 작성 과정에서 테스트를 실행하거나 발견 사항을 수정하지 않았다.

원본 타입의 파일별 대응은 [타입 대응표](upstream-type-map.md), 현재 구조는 [core 구조](core-architecture.md), Android의 저장소 차이는 [Android 대응 경계](android-adaptations.md)를 참고한다.

이 문서는 현재 남은 차이, 사용자가 선택한 변경, 추가 검증이 필요한 잠재 결함을 구분한다. 과거 조사 뒤 제거한 저장소 정책은 현재 차이로 취급하지 않는다.

## 유지된 주요 동작

| 항목 | 유지된 계약 |
| --- | --- |
| 규칙 선택 | 경로와 실행 조건이 모두 맞는 첫 번째 규칙을 선택한다. |
| FullSync | 누락 파일을 추가하고 변경 파일을 갱신하며 대상에만 있는 파일을 삭제 후보로 반환한다. |
| UpdateOnly | 추가·갱신을 수행하고 대상에만 있는 파일은 보존한다. |
| InstallOnly | 누락 파일만 추가하고 기존 대상은 비교·갱신하지 않는다. |
| Exclude | 비교·전송·삭제에서 제외한다. |
| 실행 조건 | Always 또는 OnNewVersion을 사용하며 강제 실행도 새 버전 조건을 충족한다. |
| 전송 후 검증 | 전송에 사용한 규칙의 비교기를 다시 호출한다. 불충족 시 FileIntegrityException을 전달한다. |
| 체크섬 생략 | LocalFileChecksumComparer는 대상이 존재하면 내용을 읽지 않고 true를 반환한다. true가 항상 바이트 동일성을 의미하지는 않는다. |
| 메타데이터 오류 | 크기·체크섬 메타데이터 비교기의 오류 정책과 제공된 체크섬의 유효성 검사를 유지한다. |
| 삭제 | 동기화 결과와 실제 삭제를 분리한다. 호출자가 삭제 메서드를 명시적으로 실행한다. |
| 버전 | UTF-8 128바이트 제한, trim, 파일 직접 쓰기, NullVersionManager의 항상 새 버전 판단을 유지한다. |

근거: [Kotlin 컬렉션 동기화기](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncFileCollectionSyncer.kt), [병렬 전송기](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/ParallelSyncFilePairSyncer.kt), [로컬 체크섬 비교기](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/LocalFileChecksumComparer.kt).

## 의도적으로 선택한 동작 차이

| 항목 | .NET | Kotlin | 영향 |
| --- | --- | --- | --- |
| glob 구현 | DotNet.Glob 3.1.3 | 자체 GlobPattern | 원본 라이브러리의 전체 문법·특이 동작은 복제하지 않는다. |
| glob 문법 | 문자 집합 등 추가 문법 지원 | `*`, `?`, 독립된 `**` | `[a-z]` 같은 원본 규칙을 그대로 가져오면 의미가 달라진다. 대괄호·중괄호는 일반 문자다. |
| 대소문자 비교 | StringComparer.OrdinalIgnoreCase와 StringComparison.OrdinalIgnoreCase | String.CASE_INSENSITIVE_ORDER, String/Char의 ignoreCase | Unicode 이름의 동일성·중복 판정이 달라질 수 있다. Kotlin은 `I`/`ı`, `İ`/`i`도 대소문자 무시 시 같은 이름으로 취급한다. |
| 스트림 취소 | ReadAsync·WriteAsync 등에 CancellationToken 전달 | 버퍼 작업 사이에서 코루틴 취소 확인 | blocking I/O가 반환될 때까지 취소가 늦어질 수 있다. 강제 인터럽트·자동 닫기는 하지 않는다. |
| 진행률 누적 | 스레드별 값을 ThreadLocal에 누적 | AtomicReference의 합계를 갱신 | 스레드를 이동하는 코루틴에 맞춘 구현이다. 동시 집계·초기화의 세부 시점은 같지 않다. |

glob의 `*`·`?`는 경로 요소 안에서만 일치한다. 경로 요소 전체가 `**`일 때만 디렉터리 경계를 넘는다. `ab**cd`나 `***`는 하나의 경로 요소 안에서 처리한다. 재귀·백트래킹과 외부 matcher 의존성을 사용하지 않는다.

버전 문자열의 Unicode 공백 보정은 제거하지 않았다. [DotNetWhitespace](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/internals/DotNetWhitespace.kt)는 .NET String.Trim의 공백 집합을 유지하기 위한 작은 보조 구현이다. 대소문자 비교의 .NET 보정 제거와는 별개다.

근거: [GlobPattern](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/internals/GlobPattern.kt), [SyncPathComparer](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncPathComparer.kt), [스트림 처리](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/internals/StreamProgressHelper.kt), [진행률 집계](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/progress/ConcurrentByteProgressAggregator.kt).

## 추가로 발견한 동작 차이

### 규칙 패턴 전처리

원본 SyncRuleEvaluator는 패턴의 구분자만 치환하고 Glob.Parse에 전달한다. Kotlin은 패턴에도 PathHelper.normalizePath를 적용한다. 실제 파일 경로 정규화와 패턴 전처리가 같은 처리가 된 상태다.

| 입력 | 원본 소스·테스트의 계약 | Kotlin 소스에서 도출한 동작 |
| --- | --- | --- |
| 패턴 `mods//./+*`, 경로 `mods/+example.jar` | 구분자만 치환한 원본 패턴으로 평가하여 불일치 | 패턴을 `mods/+*`로 정규화하므로 일치 |
| 패턴 `../outside/**` | 파싱 후 상대 경로 패턴으로 평가하며, `outside/file`과 불일치 | normalizePath에서 부모 경로 요소를 거부하므로 생성 단계에서 예외 |

이 차이는 자체 glob의 지원 문법을 줄이는 결정과 분리해서 검토할 수 있다. Kotlin 쪽 결과는 이번 조사에서 실행한 재현 결과가 아니라 소스에 따른 판정이다.

근거: [원본 규칙 평가기](https://github.com/AlphaBs/FishSyncClient/blob/f7dad08cf62ca23c1feec44bafe576c4da1b2bce/src/Syncer/SyncRuleEvaluator.cs), [원본 패턴 테스트](https://github.com/AlphaBs/FishSyncClient/blob/f7dad08cf62ca23c1feec44bafe576c4da1b2bce/test/SyncRulesTests.cs), [Kotlin 규칙 평가기](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncRuleEvaluator.kt).

### 빈 패턴 검증

원본 테스트는 빈 패턴을 잘못된 설정으로 규정하고, 파일 탐색이나 전송 전에 거부하도록 한다. Kotlin은 rules 목록의 존재·개수와 comparer는 검사하지만 rule.pattern의 빈 문자열을 검사하지 않는다.

따라서 Kotlin에서는 빈 패턴이 컴파일되고, 대상 경로에 따라 나중에 규칙 미일치 오류가 발생하거나 파일이 없으면 비교가 성공할 수 있다. 원본과 같은 조기 오류 계약을 유지할지 결정할 필요가 있다.

원본은 숫자를 강제 캐스팅한 잘못된 enum 값도 검사한다. Kotlin enum은 일반 Kotlin 호출에서 그런 값을 만들 수 없으므로 동일한 숫자 범위 검사가 없는 것 자체를 누락으로 보지는 않는다.

### 병렬 실패 처리와 대기 작업

두 구현 모두 기본 병렬도는 CPU 수에 따라 1~4개이며, 작업 종료를 관찰한 뒤 반환한다. 내부 실행 방식은 다르다.

| 항목 | .NET | Kotlin |
| --- | --- | --- |
| 실행 단위 | Dataflow ActionBlock에 파일 쌍을 전달 | 파일마다 launch하고 Semaphore로 실행 수 제한 |
| 실행 제한 | MaxDegreeOfParallelism | Semaphore permit 수 |
| 대기 상태 | 블록에 메시지를 보관 | permit을 기다리는 코루틴을 생성 |
| 실패 전파 | 블록 실패와 Completion 관찰 | coroutineScope 실패로 다른 자식 작업에도 취소 전달 |
| 결과 보관 | ConcurrentBag | ConcurrentLinkedQueue |

입력 수만큼 코루틴이 만들어질 수 있으므로 동시 실행 수 제한이 전체 코루틴 개수 제한은 아니다. 원본도 대기열 용량을 제한하지 않으므로 원본이 고정 메모리로 실행된다고 주장할 수는 없다.

Kotlin의 실패 시 다른 자식 취소는 [coroutineScope 계약](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/coroutine-scope.html)에 따른다. 이 구조와 원본의 토큰 전달 방식을 대조하면 실패 시 다른 파일의 완료·중단 결과가 달라질 가능성이 있다. 정확한 적용 파일 집합과 예외 우선순위는 동일 입력·실패 시점의 대조 테스트가 필요하다.

병렬 결과의 순서도 두 구현에서 동일하다고 보장하지 않는다. ConcurrentBag과 ConcurrentLinkedQueue의 차이뿐 아니라 작업 완료 순서도 영향을 준다.

근거: [원본 병렬 전송기](https://github.com/AlphaBs/FishSyncClient/blob/f7dad08cf62ca23c1feec44bafe576c4da1b2bce/src/Syncer/ParallelSyncFilePairSyncer.cs), [Kotlin 병렬 전송기](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/ParallelSyncFilePairSyncer.kt).

### HTTP 스트림 설정

core의 ReadableHttpSyncFile도 비교 범위에 포함한다. 원본은 HttpClient와 Stream을, Kotlin은 OkHttpClient와 InputStream을 사용한다.

원본은 반환 스트림이 CanTimeout을 지원하면 ReadTimeout을 10초로 설정한다. Kotlin의 해당 클래스는 별도 값을 설정하지 않고 주입된 OkHttpClient 설정에 의존한다. 기본값이 우연히 같더라도 클래스가 보장하는 정책은 다르다. HTTP 오류의 예외 타입과 메시지도 원본과 같지 않다.

Content-Length가 있으면 등록된 메타데이터 크기와의 차이를 진행률에 반영하는 역할과, 스트림을 닫을 때 응답을 정리하는 역할은 유지한다.

근거: [원본 HTTP 파일](https://github.com/AlphaBs/FishSyncClient/blob/f7dad08cf62ca23c1feec44bafe576c4da1b2bce/src/Files/ReadableHttpSyncFile.cs), [Kotlin HTTP 파일](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/ReadableHttpSyncFile.kt).

### 컬렉션 조회와 동등성

- 원본 SyncFileProgressCollection.GetItems는 Dictionary.Values 뷰를 반환한다. Kotlin getItems는 toList로 항목 목록을 복사한다. 조회 결과를 보관한 뒤 파일을 추가하면 Kotlin의 기존 목록에는 새 항목이 나타나지 않는다.
- 목록 복사는 항목 객체 자체의 깊은 복사가 아니다. 기존 항목의 progress·eventType 변경은 보관한 항목 객체에서도 보인다.
- Kotlin의 SyncFileProgressCollection.iterator도 항목을 리스트로 변환한다. 원본의 Dictionary 열거와 수정 중 열거 동작까지 같지는 않다.
- FileProgressEvent, SyncFileByteProgress와 중첩된 SyncFileProgressItem은 원본에서 일반 클래스지만 Kotlin에서는 data class다. 별도 객체라도 필드 값이 같을 때 equals 결과가 달라질 수 있다.
- ConcurrentByteProgressAggregator.close는 Kotlin에서 아무 작업도 하지 않는다. 원본 Dispose는 ThreadLocal을 폐기한다. 폐기 이후 사용까지 같은 수명 계약은 아니다.

근거: [Kotlin 파일 진행률 컬렉션](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/progress/SyncFileProgressCollection.kt), [원본 파일 진행률 컬렉션](https://github.com/AlphaBs/FishSyncClient/blob/f7dad08cf62ca23c1feec44bafe576c4da1b2bce/src/Progress/SyncFileProgressCollection.cs).

### enum 숫자와 오류 표현

| 항목 | .NET 값 | Kotlin ordinal |
| --- | --- | --- |
| SyncAction.FullSync | 1 | 0 |
| SyncAction.InstallOnly | 2 | 2 |
| SyncAction.Exclude | 3 | 3 |
| SyncAction.UpdateOnly | 4 | 1 |
| SyncCondition.Always | 1 | 0 |
| SyncCondition.OnNewVersion | 2 | 1 |

이름으로 비교하는 현재 규칙 로직과 숫자 표현의 호환성은 구분해야 한다. ordinal을 원본 enum 숫자로 저장·전송하면 호환되지 않는다. 숫자 호환이 필요하면 명시적인 변환이 필요하다.

ArgumentException·InvalidOperationException·KeyNotFoundException 등은 Kotlin의 IllegalArgumentException·IllegalStateException·NoSuchElementException 등으로 대응한다. 중복 경로의 출처, 규칙 번호·필드명, 잘못된 체크섬의 상세 사유를 담는 메시지도 Kotlin에서 더 간략하다. 예외 타입·문자열까지 동일한 계약은 아니다.

## 잠재 결함: 취소 시 열린 로컬 스트림이 유실될 가능성

상태: 정적 분석으로 발견. 전용 재현 테스트와 수정은 아직 없음.

LocalSyncFile.openReadStream과 openWriteStream은 withContext(Dispatchers.IO) 안에서 스트림을 열어 밖으로 반환한다.

```kotlin
withContext(Dispatchers.IO) {
    File(fullPath).inputStream()
}
```

호출자가 다른 dispatcher에 있고 스트림을 연 뒤 반환하기 전에 취소되면, withContext가 반환값 대신 CancellationException을 전달할 수 있다. 스트림을 받을 호출자가 없어지고 이 경로에 별도 close도 없으므로 자원이 누수될 위험이 있다. 읽기·쓰기 모두 확인 대상이다. 같은 dispatcher에서 호출되는 모든 경로에 반드시 누수가 발생한다는 의미는 아니다.

원본의 로컬 스트림 열기는 토큰을 확인한 뒤 동기적으로 열어 완료된 ValueTask로 반환하므로 해당 dispatcher 복귀 경계를 만들지 않는다. Kotlin의 자원 전달 방식에서 생긴 위험으로 분류한다.

이 항목은 버퍼 사이의 취소 확인이나 강제 스트림 취소 지원과 별개다. 강제 취소를 다시 도입하지 않고도 자원 소유권 전달을 정리할 수 있다.

근거: [LocalSyncFile](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/LocalSyncFile.kt), [withContext의 자원 반환 주의사항](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html#pitfalls).

## 코드 구조 차이

| 항목 | .NET | Kotlin | 분류 |
| --- | --- | --- | --- |
| 인터페이스 | IFileComparer 등 I 접두사 | FileComparer 등 접두사 제거 | 합의한 Kotlin 명명 |
| 충돌하는 구현 이름 | SyncFileCollection, VersionManager | DefaultSyncFileCollection, FileVersionManager | 합의한 Kotlin 명명 |
| 파일 구성 | 일부 파일에 인터페이스·구현·결과 타입 함께 선언 | 주된 타입별 파일 분리 | Kotlin 코드 구성 |
| namespace | SyncFileCollectionSyncer 등 일부 타입의 namespace와 디렉터리 불일치 | 소문자 패키지와 디렉터리를 일치 | 패키지 정리 |
| 비동기 API | Task/ValueTask와 CancellationToken 인자 | suspend와 호출 코루틴 문맥 | 런타임 대응 |
| 진행률 API | IProgress 구현·인스턴스 | 타입별 함수 콜백과 report 메서드 | 언어 대응 |
| 스트림 | 읽기·쓰기를 포괄하는 Stream | InputStream과 OutputStream 분리 | JVM 대응 |
| 파일 메타데이터 | init 속성으로 초기화 | 생성자 val로 전달 | 불변 초기화 방식 |
| 값 객체 | readonly struct·record 등 | 클래스·data class | 값/참조 및 동등성 차이 검토 필요 |
| 로컬 크기 조회 | 비교기에서 FileInfo.Length 조회 | LocalSyncFile.length 재정의 가능 | 경로 없는 저장소 연결 경계 |
| 경로 인덱스 | 해시 Dictionary | 정렬 Map과 입력 목록 복사 | 알고리즘·할당 차이 |
| 실행 의존성 | DotNet.Glob, Dataflow, .NET API | 자체 glob, coroutines, OkHttp, JVM API | 실행 환경 차이 |

SyncPathComparer의 Kotlin 인덱스 생성은 대체로 O(n log n)이며 원본의 해시 인덱스는 평균 O(n)이다. Kotlin은 원본 입력 순서로 결과를 구성하기 위해 source·target 목록도 보관한다. 목록 순서 유지와 내부 탐색 자료구조는 별개다. 실제 성능 비교를 실행한 결과는 아니다.

LocalSyncFile.exists와 length를 재정의할 수 있게 한 것은 SAF 같은 대상을 로컬 비교기에 연결하기 위한 경계다. 실제 파일 경로를 가지지 않는 객체가 같은 비교기 계약에 참여한다. core 자체에는 Android ContentResolver나 URI 타입을 넣지 않는다.

## 현재 storage 계층과 제거 완료된 정책

원본에는 ReadableSyncStorage·WritableSyncStorage·StorageLocalFile·StorageSyncer 같은 저장소 추상화가 없다. Kotlin에는 일반 파일과 SAF 접근을 연결하기 위한 어댑터가 남아 있다.

현재 StorageSyncer는 SyncFileCollectionSyncer를 상속한다. 대상 목록과 새 파일 쌍을 연결할 뿐 원본과 다른 전송 루프·검증 알고리즘을 구현하지 않는다. 파일 탐색은 규칙 검증 뒤에 지연 실행하고, 삭제는 deleteFiles를 명시적으로 호출한다.

| 과거에 추가했던 차이 | 현재 상태 |
| --- | --- |
| SyncPlan·expectedPlan·fingerprint를 이용한 미리보기 후 적용 | 제거됨 |
| StorageLocks·StorageSnapshot의 잠금·대상 변경 재검사 | 제거됨 |
| PendingWrite·임시 파일 검증 후 교체·SAF 백업 복구 | 제거됨. 대상 직접 쓰기 |
| DefaultStorageFilePairSyncer의 별도 크기·체크섬 검증 | 제거됨. 원본 규칙 비교기로 재검증 |
| 전송 성공 후 로컬 자동 삭제 | 제거됨. 삭제 후보만 반환 |
| SyncResult·SyncFailure의 적용 목록·부분 실패 결과 | 제거됨. 원본 결과 타입과 원래 예외 사용 |

따라서 이전 조사에서 지적한 storage 전용 순차 전송, 별도 검증, 자동 삭제, 부분 실패 포장은 현재 남아 있는 차이가 아니다. 원본 병렬 전송기를 사용하므로 앞서 기술한 Kotlin 병렬 처리 차이는 공통으로 적용된다.

core 밖의 PUSH 업로드용 임시 복사본이나 서버가 실행하는 삭제 작업은 이 문서의 로컬 적용 정책 제거와 구분한다.

근거: [StorageSyncer](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/storage/StorageSyncer.kt), [StorageLocalFile](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/storage/StorageLocalFile.kt), [직접 쓰기 저장소 계약](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/storage/WritableSyncStorage.kt).

## 검증 범위와 후속 확인 항목

기준 Kotlin 커밋의 [CI](https://github.com/AlphaBs/AndroidFishSyncClient/actions/runs/38696539824)는 통과했다. CI의 원본 참조는 workflow에 고정한 `79840441f8398a0934573664e58f07d9f1fe1caf`이다. 이 문서의 로컬 원본 조사 커밋과 CI 참조 커밋을 같은 식별자로 취급하지 않는다.

CI는 .NET 비교 fixture 9개와 버전 공백 fixture 8개, Kotlin 자체 glob 문법, JVM·HTTP 테스트, Android API 26·35 계측, 앱 빌드와 lint를 검증한다. 자체 glob의 작은 입력 전수 비교는 자체 문법의 검증이며 DotNet.Glob 전체와의 일치 검증이 아니다.

| 우선 확인 항목 | 현재 상태 | 필요한 확인 |
| --- | --- | --- |
| LocalSyncFile 스트림 반환 시 취소 | 잠재 자원 누수 위험 | 열기 완료와 dispatcher 복귀 사이에 취소를 주입하는 재현·정리 검증 |
| 빈 규칙 패턴 | 원본과 검증 계약 다름 | 파일 탐색 이전에 실패해야 하는지 결정하고 계약 테스트 추가 |
| 패턴의 normalizePath 적용 | 입력별 의미·오류 시점 다름 | 패턴 전처리 정책 결정 후 원본 사례 대조 |
| 병렬 작업 실패 | 실행 모델 다름 | 다른 작업이 전송 중일 때 실패·입력 열거 예외를 주입해 적용 결과와 예외 전달 대조 |
| HTTP 설정 | 주입된 클라이언트에 의존 | 읽기 제한·오류 타입의 호환 범위 명시 |
| enum 숫자·이벤트 동등성·조회 뷰 | API 표현 차이 | 숫자 저장이나 equals, 보관한 조회 결과를 쓰는 호출자가 있는지 확인 |

후속 항목의 수정·재현 테스트는 이 문서 작성 범위에 포함하지 않았다. CI 통과만으로 모든 원본 입력과 오류 상황에서 동일한 동작을 보장하지 않는다.
