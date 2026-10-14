# 원본 core 구조·동작 차이 감사

작성일: 2026-10-15. 정적 코드 대조 결과이며 새 테스트를 로컬에서 실행하지 않았다.

원본 기준은 `FishSyncClient/src` 로컬 커밋 `f7dad08cf62ca23c1feec44bafe576c4da1b2bce`다. 포팅 계획의 원격 기준 `79840441f8398a0934573664e58f07d9f1fe1caf`와 이 커밋 사이에 src 변경이 없음을 기존 조사에서 확인했다. Kotlin은 이번 core 인터페이스·패키지 재구성과 `SyncFileCollectionSyncer`/`LocalSyncer` 수정까지 포함한 작업 내용을 기준으로 한다.

## 결론

현재 구현은 원본의 일부 동작을 제공하는 재설계에 가깝다. 디렉터리를 맞췄지만 객체 모델과 확장 계약을 일대일로 포팅한 상태는 아니다. Kotlin 문법과 Coroutines로 바꿔도 유지할 수 있었던 역할을 축소하거나 합친 부분이 있다. 아래 항목들은 추가 수정이 필요하다.

## 1. 파일 객체와 컬렉션

| 원본 | 현재 Kotlin | 차이와 영향 |
| --- | --- | --- |
| 추상 `SyncFile` | `SourceFile`, `FileEntry`, `SyncStorage` | 원본·대상을 같은 파일 추상형으로 다루지 않는다. 읽기/쓰기 능력, 스트림 열기, `CopyTo`, 경로·메타데이터의 공통 계약이 없다. |
| `LocalSyncFile` | `FileSyncStorage` | 개별 파일 객체가 저장소 전체 객체로 바뀌었다. 원본의 파일별 Exists·읽기·쓰기·복사 API가 없다. |
| `ReadableHttpSyncFile` | client의 `HttpTransport.openDownload`와 `SourceFile.open` | 원본은 core 파일 객체다. 현재는 HTTP 원본 객체 없이 client의 람다로 연결한다. `Uploaded`와 HTTP Content-Length를 진행률에 반영하는 동작도 없다. |
| `VirtualSyncFile` | 직접 대응 없음 | 읽기·쓰기를 지원하지 않는 메타데이터 전용 파일을 표현하는 공통 타입이 없다. |
| `SyncFileMetadata` | 각 타입의 size/checksum 필드 | 메타데이터 객체가 분산됐다. 대상 `FileEntry`에는 checksum 자체가 없다. |
| `SyncFileChecksum` | `Checksum` | 이름·구조와 검증 시점이 다르다. 원본은 비교기가 메타데이터를 검증하며 Kotlin은 생성 시 검증한다. |
| `SyncFilePair` | 같은 이름의 데이터 클래스 | 원본은 SyncFile 두 개와 `SyncContent` 동작을 가진다. Kotlin은 SourceFile과 FileEntry의 묶음이다. |
| `ISyncFileCollection`, `SyncFileCollection` | List와 내부 Map | 컬렉션 인터페이스와 `FindFileByPath` 확장 계약이 없다. |

`ReadableSyncStorage`, `WritableSyncStorage`, `SyncStorage`, `PendingWrite`는 원본에 없는 Android/SAF 및 안전한 파일 교체용 확장이다. 이 확장은 원본 파일 모델과 함께 설계할 수 있으며, 원본 모델 전체를 없애야 하는 이유는 아니다.

## 2. 비교기와 규칙

| 원본 | 현재 Kotlin | 차이와 영향 |
| --- | --- | --- |
| `IFileComparer` | `FileComparer` | 원본은 파일 쌍만 받지만 Kotlin은 별도 저장소도 받는다. 파일 추상화 차이가 비교기 계약까지 전파됐다. |
| `IFileComparerFactory`, `LocalFileComparerFactory` | `FileComparerFactory`, `DefaultFileComparerFactory` | 원본의 CreateFullComparer/CreateFastComparer가 enum 기반 create로 바뀌었다. 이름 변경을 넘어 확장 API가 다르다. |
| `SyncRule.Comparer` | `SyncRule.comparison: Comparison` | 원본은 규칙마다 비교기 객체를 지정한다. Kotlin은 세 가지 enum 중 선택한다. 팩터리 전역 주입은 가능하지만 규칙별 비교기 주입과 같은 계약은 아니다. |
| `LocalFileSizeComparer` | `FileSizeComparer` | 원본은 원본 크기 누락 시 FileComparerException이다. Kotlin은 source.size가 null이면 true다. 공개 비교기 단독 호출에서 대상 존재 확인 계약도 다르다. |
| `LocalFileChecksumComparer` | `FileChecksumComparer` | 원본은 대상 존재 여부를 확인한 뒤 checksum 생략을 처리한다. Kotlin은 checksum이 없으면 저장소 확인 없이 true다. 컬렉션의 경로 분류와 비교기 자체의 보장을 구분해야 한다. |
| `FileSizeMetadataComparer`, `FileChecksumMetadataComparer` | 대응 없음 | 양쪽 메타데이터만 비교하는 정책이 없다. 대상 메타데이터 모델도 부족하다. |
| `ComparerErrorHandlingModes` | 대응 없음 | 비교 불가 시 ReturnEqual/ReturnNotEqual/ThrowException 선택이 없다. |
| `CompositeFileComparerWithGlob` | `CompositeFileComparer` | 원본은 처음 일치한 glob의 비교기 하나를 선택한다. Kotlin은 모든 비교기를 AND로 실행한다. 두 클래스는 같은 역할이 아니다. |
| `ChecksumMetadataValidator`, `FileComparerException` | Checksum.init, 일반 예외 | 검증 객체와 오류 타입이 축소됐다. 원본의 source/target 구분과 비교 불가 오류를 같은 방식으로 다룰 수 없다. |

Kotlin의 AND 합성 비교기는 원본에 없는 추가 객체다. 원본의 glob 선택 비교기를 포팅했다고 설명하면 안 된다.

원본의 전송 후 검증은 해당 규칙의 비교기를 다시 사용한다. Kotlin 기본 전송기는 규칙의 비교기와 독립적으로 크기와 제공된 checksum을 검증한다. 원본 구조로 옮기더라도 기존 파일 보호와 무결성 검증을 약화시키지 않도록 두 책임을 명시해야 한다.

## 3. 동기화 조율과 결과

| 원본 | 현재 Kotlin | 차이와 영향 |
| --- | --- | --- |
| `SyncFileCollectionSyncer` | 이번 변경에서 이름 수정 | 최초 Kotlin 구현의 SyncEngine은 원본에 없는 이름이었다. 이름을 맞췄어도 현재는 저장소 기반 조율기이며 원본의 파일 컬렉션 조율기와 완전히 같지 않다. |
| `LocalSyncer : SyncFileCollectionSyncer` | 위임 방식의 LocalSyncer | 현재 일반 파일·SAF 저장소를 고정하는 래퍼다. 원본의 상속, EnumerateLocalSyncFiles, CreateFilePairs 확장점, DeleteLocalFiles API가 없다. |
| `SyncPathComparer`, `SyncFilePathCompareResult` | 조율기 내부 indexByPath와 분류 | 경로 비교 객체와 결과 모델이 분리되지 않았다. |
| `ISyncFilePairSyncer` | `SyncFilePairSyncer` | 원본은 CompareFilePairs/SyncFilePairs/CompareAndSyncFilePairs라는 컬렉션 계약이다. Kotlin은 단일 파일 sync만 제공한다. |
| `ParallelSyncFilePairSyncer` | `DefaultSyncFilePairSyncer` | 원본은 기본 CPU 수 기준 최대 4개이며 병렬도 지정도 가능하다. Kotlin은 조율기의 순차 for 루프다. 병렬 전송기 구현이 없다. |
| `SyncerOptions` | 메서드 인자와 공유 Flow | rules, context, 파일·바이트 진행률, 취소를 묶는 실행별 옵션 객체가 없다. Kotlin에서 취소 토큰을 코루틴 문맥으로 바꾸는 것은 자연스러운 대응이지만 다른 옵션까지 없애야 하는 것은 아니다. |
| 컬렉션·파일 쌍 비교 결과 세 종류 | `SyncPlan`, `SyncResult` | 원본은 파일 객체와 파일 쌍을 반환한다. Kotlin은 경로 문자열과 fingerprint, 적용·삭제 목록을 반환한다. 결과의 정보와 사용 방식이 다르다. |
| `FileIntegrityException.File` | `IntegrityException` | 이름이 다르고 파일 경로를 구조화된 프로퍼티로 제공하지 않는다. |

원본은 로컬 삭제를 별도 호출로 노출한다. Kotlin은 전송이 모두 성공한 뒤 삭제를 자동 실행한다. `SyncFailure`, `ChangedFileException`, 상태 fingerprint, 저장소 잠금은 원본에 없는 추가 기능이다. 삭제 계약은 소비자에게 보이는 동작 차이이므로 단순 이름 변경과 분리해서 다뤄야 한다.

### 추가로 발견한 비교 동작 차이

현재 컬렉션 동기화기는 비교 결과의 fingerprint를 만들기 위해 **대상 파일 전부를 다시 열어 해시한다**. 적용 직전의 원본 상태 수집도 전체 대상 내용을 읽는다. 원본은 선택된 규칙과 비교기에 필요한 파일만 비교한다.

따라서 Kotlin에서는 Exclude 파일이나 크기 비교만 하는 파일, checksum을 생략한 파일의 내용도 읽는다. 제외한 파일을 읽을 수 없으면 전체 비교가 실패할 수 있고, 크기 비교의 비용도 전체 파일 해시 수준이 된다. 이 차이는 확장 기능을 추가하면서 원래 비교 정책을 침범한 부분이므로 우선 수정 대상이다. 미리보기 보호를 유지하되 적용 대상과 관련된 파일만 적절한 정책으로 검증하도록 분리해야 한다.

## 4. 진행률

원본에는 ByteProgress, SyncFileByteProgress, FileProgressEventType, FileProgressEvent, ByteProgressAggregator, ConcurrentByteProgressAggregator, SyncFileProgressCollection이 있다. Kotlin은 `SyncProgress(path, phase: String, bytes, total)`로 축소했다.

- 파일 큐·비교 시작/완료·동기화 시작/완료의 타입화된 이벤트가 없다.
- 전체 파일 수, 완료 수, 파일별 상태 컬렉션과 전체 바이트 집계가 없다.
- 원본은 동일 파일의 바이트도 전체 진행률에 반영한다. Kotlin은 동일 파일 진행 이벤트를 보내지 않는다.
- 원본의 HTTP Content-Length에 따른 총량 보정과 로컬 파일의 주기적 진행 보고가 없다. Kotlin은 고정 버퍼 전송 이벤트를 보낸다.
- 현재 Flow는 조율기 인스턴스에서 공유한다. 실행별 콜백과 수명 범위가 다르다.
- MutableSharedFlow.tryEmit의 반환값을 무시하므로 느린 구독자에게 모든 이벤트가 전달된다고 보장할 수 없다.

Flow 자체는 Kotlin에 적합하지만 원본의 상태·집계 모델을 대체하지는 않는다. 원본 Internals.SyncProgress<T>는 IProgress 콜백 어댑터이며 Kotlin Progress.SyncProgress와 이름만 비슷하고 역할은 다르다.

## 5. 경로, glob, 해시

- `RootedPath`, `PathOptions`, `PathHelper`가 없다. Kotlin은 relativePath 함수, FileSyncStorage 루트, caseInsensitive 인자로 나눴다. WithRoot, 디렉터리 경로 표현, 구분자 설정 같은 API가 없다.
- 원본 NormalizePath는 중복 구분자와 `.` 세그먼트를 정리한다. Kotlin relativePath는 `a//b`, `a/./b`를 거부한다. 둘 다 `..`를 거부하지만 정규화 계약 전체가 같지는 않다.
- 원본 LocalPathGuard는 루트 및 조상 링크를 검사한다. Kotlin FileSyncStorage는 루트를 canonicalFile로 해석한 뒤 루트 내부 링크를 거부한다. Android 경로 별칭 대응과 함께 의도적으로 설계해야 할 차이다.
- 원본의 OrdinalIgnoreCase와 Kotlin의 Locale.ROOT lowercase 기반 키가 모든 Unicode 경로에서 같다고 검증하지 않았다.
- 원본 DotNet.Globbing을 자체 Regex 변환기로 대체했다. Kotlin은 `{`/`}`를 명시적으로 거부한다. 현재 fixture 일부가 맞는 것과 전체 glob 문법 호환성은 다르다.
- `ChecksumAlgorithms`, `ChecksumAlgorithmNames`, `HashHelper`는 hash 함수와 문자열 상수로 축소했다. md5/sha1 및 hex 출력은 대응하지만 원본의 동기/비동기 보조 API는 그대로 제공하지 않는다.
- 원본 StreamProgressHelper의 버퍼 선택·주기 보고 도구를 옮기지 않고 여러 전송 루프에서 직접 처리한다.

## 6. 버전 관리

원본은 `IVersionManager` 인터페이스와 `VersionManager` 구현이다. Kotlin은 `VersionManager`가 인터페이스이고 파일 구현은 `VersionState`다. VersionState는 원본에 없는 이름이며, 단순히 I 접두사를 없앤 결과로 구현체 이름까지 달라졌다.

- 원본은 비동기 API이고 Kotlin 버전 관리자는 동기 파일 I/O API다.
- UTF-8 128바이트 제한과 trim·새 버전 비교는 대응한다.
- 원본 읽기는 모든 예외를 null로 처리하지만 Kotlin은 IOException만 처리한다.
- 원본 갱신은 직접 파일 생성·쓰기다. Kotlin은 부모 디렉터리 생성과 임시 파일의 원자적 교체를 추가했다.
- NullVersionManager는 원본처럼 항상 새 버전으로 판단한다. 이 동작은 최근 분리 과정에서 원본과 다시 대조해 맞췄다.

## 7. Android·안전성 확장과 원본 src 밖의 기능

SAF, URI 권한 유지, PendingWrite의 임시 파일·백업 적용, 미리보기 fingerprint, 업로드 스냅샷, 같은 루트 잠금, 부분 적용 결과는 추가 기능이다. 원본 구조를 복원하는 과정에서 이 기능들을 없애지 않고 어댑터나 확장 정책으로 분리해야 한다.

현재 fishsync-client의 로그인·버킷 조회·PULL/PUSH 서버 조율은 원본 FishSyncClient/src에 직접 대응하는 클래스가 없다. 이 부분은 기존 계획대로 FishSyncServer 측 계약에서 가져온 별도 범위다. core 객체 모델 감사와 서버 API 완전성 검증을 혼동하지 않는다.

## 8. 파일 단위와 패키지의 남은 차이

현재 SyncAction, SyncCondition, SyncContext, Comparison, SyncRule은 SyncRule.kt에 모여 있다. 여러 예외도 SyncExceptions.kt에 묶여 있다. 원본의 타입별 소스 파일 구성까지 전부 맞춘 것은 아니다.

또한 원본 SyncFileCollectionSyncer.cs는 Syncer 디렉터리에 있지만 namespace는 FishSyncClient 루트다. Kotlin은 디렉터리와 패키지를 일치시키려고 core.syncer에 두었다. 현재 구성은 원본 디렉터리 역할을 따른 것이며 원본 네임스페이스의 문자 그대로의 복제는 아니다.

## 수정 우선순위

1. Exclude·크기 비교까지 전체 파일 해시가 강제되는 fingerprint 경계를 먼저 바로잡는다. 이어서 SyncFile, 메타데이터, 경로와 컬렉션의 공통 모델을 먼저 복원한다. 일반 파일·HTTP·SAF 연결 위치와 안전한 교체 확장을 함께 정한다.
2. SyncRule.Comparer와 로컬/메타데이터 비교기, glob 선택 비교기, 비교 오류 정책을 복원한다.
3. SyncPathComparer와 컬렉션 결과를 분리하고, 파일 쌍 동기화 인터페이스를 컬렉션 계약으로 맞춘다. LocalSyncer의 역할과 삭제 계약을 확정한다.
4. 제한된 병렬 전송과 원본 진행률 상태·집계 객체를 옮긴다. 코루틴·Flow는 전달 방식으로 사용한다.
5. 버전 관리자 이름·비동기 계약과 경로/해시 공개 API를 맞추고 원본 경계 테스트를 CI에 추가한다.

현재 공통 fixture는 9개 비교 사례이며 원본 전체 객체 계약의 호환성을 입증하지 않는다. 이미 통과한 과거 테스트 기록도 이번 감사에서 발견한 차이가 없다는 의미로 해석하면 안 된다.
