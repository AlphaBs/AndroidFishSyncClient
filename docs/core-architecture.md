# core 객체 구조

원본 FishSyncClient/src의 최상위 객체와 인터페이스 책임을 유지한다. 이름은 Kotlin 관례에 맞춰 인터페이스의 I 접두사를 제거하고, 이름이 겹치는 구현체는 역할을 드러내도록 구분한다. 전체 원본 타입과 Kotlin 소스 파일의 대응은 [타입 대응표](upstream-type-map.md)에 기록한다.

| 패키지 | 원본 역할 |
| --- | --- |
| core 루트 | RootedPath, PathOptions, PathHelper, ChecksumAlgorithms, ChecksumAlgorithmNames, HashHelper |
| files | 추상 SyncFile, LocalSyncFile, ReadableHttpSyncFile, VirtualSyncFile, 파일 메타데이터·쌍·컬렉션 |
| filecomparers | FileComparer, FileComparerFactory, 로컬·메타데이터 비교기, glob 비교기와 오류 정책 |
| syncer | SyncPathComparer, 규칙·옵션·결과, SyncFilePairSyncer, ParallelSyncFilePairSyncer, SyncFileCollectionSyncer, 상속형 LocalSyncer |
| progress | ByteProgress, 파일·바이트 이벤트, 순차·동시 집계와 파일 상태 컬렉션 |
| versions | VersionManager, 파일 구현 FileVersionManager, NullVersionManager |
| internals | 원본 경로·스트림·진행률 보조 객체 및 자체 glob 매처·스트림 진행률 보조 |
| storage | 일반 파일·SAF 저장소 접근을 원본 API에 연결하는 어댑터 |

## 원본 계약으로 조립

```kotlin
val options = SyncerOptions(
    rules = listOf(
        SyncRule(
            action = SyncAction.FullSync,
            condition = SyncCondition.Always,
            pattern = "**",
            comparer = LocalFileChecksumComparer(),
        ),
    ),
)
val syncer = LocalSyncer(
    root = rootDirectory,
    pathOptions = PathOptions(),
    filePairSyncer = ParallelSyncFilePairSyncer(4),
)
val result = syncer.compareAndSyncFiles(sourceFiles, options)
// 원본처럼 삭제는 별도로 호출한다.
syncer.deleteLocalFiles(result.deletedFiles)
```

규칙마다 FileComparer를 전달한다. CompositeFileComparerWithGlob은 처음 일치하는 패턴의 비교기 하나를 선택한다. 메타데이터 비교기는 오류 처리 모드를 지원하며 로컬 비교기와 구분한다. 원본처럼 전송 후 같은 규칙의 비교기로 검증한다.

경로의 대소문자 비교는 Kotlin 표준 비교기와 ignoreCase 옵션을 사용한다. .NET OrdinalIgnoreCase 보정 계층은 두지 않는다.

추상 SyncFile은 경로·불변 메타데이터·읽기·쓰기·복사 계약을 가진다. 일반 파일·HTTP·가상 파일과 파일 쌍·컬렉션은 공통 타입을 사용한다. 결과는 경로 문자열만이 아니라 파일 객체와 파일 쌍을 반환한다.

ConcurrentByteProgressAggregator는 ThreadLocal별 불변 진행률 값을 갱신하고 슬롯 목록을 합산한다. 종료된 스레드의 값도 보존한다. clear는 저장소를 교체해 기존 스레드가 새 슬롯에 참여하게 하고 close는 보관한 값과 현재 스레드 연결을 해제한다. 보고 중 전체 합계는 단일 시점의 원자적 스냅샷이 아니며 clear·close와 진행 중인 보고 사이의 강한 원자성은 보장하지 않는다.

기본 병렬 전송기는 프로세서 수 기준 1~4개를 사용하며 병렬도를 지정할 수 있다. 큐·비교·동기화 이벤트와 바이트 델타를 제공하고 파일 상태·합계 집계 객체를 유지한다. suspend 함수와 호출 코루틴의 취소 문맥이 Task/ValueTask·CancellationToken에 대응한다.

FileVersionManager는 VersionManager의 비동기 파일 구현이다. UTF-8 128바이트 제한·trim·직접 쓰기를 유지하며 NullVersionManager는 항상 새 버전으로 판단한다.

## 앱 저장소 확장

StorageSyncer는 원본 SyncFileCollectionSyncer를 상속하며, 대상 목록과 새 대상 파일을 StorageLocalFile로 연결한다. 원본 병렬 전송과 규칙의 비교기를 그대로 사용한다. WritableSyncStorage.write는 대상에 직접 쓰고 삭제는 deleteFiles를 명시적으로 호출한다. compareFiles와 compareAndSyncFiles는 원본 SyncFileCollectionComparerResult를 반환하며 별도 적용 결과나 부분 실패 예외는 없다.

[Android 대응 경계](android-adaptations.md)에 일반 파일·앱 전용 폴더·SAF 진입점과 필수 차이와 저장소 연결 방식을 구분했다.

## 검증

테스트는 CI에서만 실행한다. 고정된 원본 소스를 체크아웃하고 .NET 비교 결과와 버전 공백 결과를 생성해 Kotlin과 대조한다. glob은 자체 문법·작은 입력의 전수 대조·긴 입력으로 별도 검증한다. 객체·비교 정책·병렬 실패·취소·진행률·버전 및 HTTP/SAF 계약을 테스트한다. Android 계측은 API 26과 35에서 실행하며 Debug/Release 빌드와 Android lint도 실행한다. 타입 대응표의 파일 존재와 전체 동작의 동일성 보장은 구분한다.
