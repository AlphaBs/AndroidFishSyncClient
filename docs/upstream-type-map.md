# 원본 core 타입 대응표

원본 `src`의 최상위 public/internal 타입 선언을 기준으로 대조한다. 디렉터리는 소문자로 옮긴다. Kotlin 인터페이스는 I 접두사를 제거하며, 이름이 겹치는 구현체는 DefaultSyncFileCollection과 FileVersionManager로 구분한다. 표의 타입 열은 원본 C# 이름이고 Kotlin 파일 열은 변경된 이름이다. `storage`는 앱 저장소와 파일 교체 확장이며 이 표의 원본 API를 대체하지 않는다.

확인한 원본 타입: 54개. 대응 파일 존재: 54개. 파일 존재는 동작 검증과 구분하며 동작은 CI의 계약·실패·계측 테스트로 검증한다.

| 원본 파일 | 타입 | Kotlin 파일 |
| --- | --- | --- |
| `ChecksumAlgorithmNames.cs` | `ChecksumAlgorithmNames` | [`ChecksumAlgorithmNames.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/ChecksumAlgorithmNames.kt) |
| `ChecksumAlgorithms.cs` | `ChecksumAlgorithms` | [`ChecksumAlgorithms.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/ChecksumAlgorithms.kt) |
| `FileComparers/ChecksumMetadataValidator.cs` | `ChecksumMetadataValidator` | [`filecomparers/ChecksumMetadataValidator.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/ChecksumMetadataValidator.kt) |
| `FileComparers/ComparerErrorHandlingModes.cs` | `ComparerErrorHandlingModes` | [`filecomparers/ComparerErrorHandlingModes.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/ComparerErrorHandlingModes.kt) |
| `FileComparers/CompositeFileComparerWithGlob.cs` | `CompositeFileComparerWithGlob` | [`filecomparers/CompositeFileComparerWithGlob.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/CompositeFileComparerWithGlob.kt) |
| `FileComparers/FileChecksumMetadataComparer.cs` | `FileChecksumMetadataComparer` | [`filecomparers/FileChecksumMetadataComparer.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/FileChecksumMetadataComparer.kt) |
| `FileComparers/FileComparerException.cs` | `FileComparerException` | [`filecomparers/FileComparerException.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/FileComparerException.kt) |
| `FileComparers/FileComparerFactory.cs` | `IFileComparerFactory` | [`filecomparers/FileComparerFactory.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/FileComparerFactory.kt) |
| `FileComparers/FileComparerFactory.cs` | `LocalFileComparerFactory` | [`filecomparers/LocalFileComparerFactory.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/LocalFileComparerFactory.kt) |
| `FileComparers/FileSizeMetadataComparer.cs` | `FileSizeMetadataComparer` | [`filecomparers/FileSizeMetadataComparer.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/FileSizeMetadataComparer.kt) |
| `FileComparers/IFileComparer.cs` | `IFileComparer` | [`filecomparers/FileComparer.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/FileComparer.kt) |
| `FileComparers/LocalFileChecksumComparer.cs` | `LocalFileChecksumComparer` | [`filecomparers/LocalFileChecksumComparer.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/LocalFileChecksumComparer.kt) |
| `FileComparers/LocalFileSizeComparer.cs` | `LocalFileSizeComparer` | [`filecomparers/LocalFileSizeComparer.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/filecomparers/LocalFileSizeComparer.kt) |
| `Files/ISyncFileCollection.cs` | `ISyncFileCollection` | [`files/SyncFileCollection.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/SyncFileCollection.kt) |
| `Files/LocalSyncFile.cs` | `LocalSyncFile` | [`files/LocalSyncFile.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/LocalSyncFile.kt) |
| `Files/ReadableHttpSyncFile.cs` | `ReadableHttpSyncFile` | [`files/ReadableHttpSyncFile.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/ReadableHttpSyncFile.kt) |
| `Files/SyncFile.cs` | `SyncFile` | [`files/SyncFile.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/SyncFile.kt) |
| `Files/SyncFileChecksum.cs` | `SyncFileChecksum` | [`files/SyncFileChecksum.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/SyncFileChecksum.kt) |
| `Files/SyncFileCollection.cs` | `SyncFileCollection` | [`files/DefaultSyncFileCollection.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/DefaultSyncFileCollection.kt) |
| `Files/SyncFileMetadata.cs` | `SyncFileMetadata` | [`files/SyncFileMetadata.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/SyncFileMetadata.kt) |
| `Files/SyncFilePair.cs` | `SyncFilePair` | [`files/SyncFilePair.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/SyncFilePair.kt) |
| `Files/VirtualSyncFile.cs` | `VirtualSyncFile` | [`files/VirtualSyncFile.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/files/VirtualSyncFile.kt) |
| `HashHelper.cs` | `HashHelper` | [`HashHelper.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/HashHelper.kt) |
| `Internals/LocalPathGuard.cs` | `LocalPathGuard` | [`internals/LocalPathGuard.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/internals/LocalPathGuard.kt) |
| `Internals/StreamProgressHelper.cs` | `StreamProgressHelper` | [`internals/StreamProgressHelper.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/internals/StreamProgressHelper.kt) |
| `Internals/SyncProgress.cs` | `SyncProgress` | [`internals/SyncProgress.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/internals/SyncProgress.kt) |
| `PathHelper.cs` | `PathHelper` | [`PathHelper.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/PathHelper.kt) |
| `PathOptions.cs` | `PathOptions` | [`PathOptions.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/PathOptions.kt) |
| `Progress/ByteProgress.cs` | `ByteProgress` | [`progress/ByteProgress.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/progress/ByteProgress.kt) |
| `Progress/ByteProgressAggregator.cs` | `ByteProgressAggregator` | [`progress/ByteProgressAggregator.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/progress/ByteProgressAggregator.kt) |
| `Progress/ConcurrentByteProgressAggregator.cs` | `ConcurrentByteProgressAggregator` | [`progress/ConcurrentByteProgressAggregator.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/progress/ConcurrentByteProgressAggregator.kt) |
| `Progress/FileProgressEvent.cs` | `FileProgressEventType` | [`progress/FileProgressEventType.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/progress/FileProgressEventType.kt) |
| `Progress/FileProgressEvent.cs` | `FileProgressEvent` | [`progress/FileProgressEvent.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/progress/FileProgressEvent.kt) |
| `Progress/SyncFileByteProgress.cs` | `SyncFileByteProgress` | [`progress/SyncFileByteProgress.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/progress/SyncFileByteProgress.kt) |
| `Progress/SyncFileProgressCollection.cs` | `SyncFileProgressCollection` | [`progress/SyncFileProgressCollection.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/progress/SyncFileProgressCollection.kt) |
| `RootedPath.cs` | `RootedPath` | [`RootedPath.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/RootedPath.kt) |
| `Syncer/FileIntegrityException.cs` | `FileIntegrityException` | [`syncer/FileIntegrityException.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/FileIntegrityException.kt) |
| `Syncer/ISyncFilePairSyncer.cs` | `ISyncFilePairSyncer` | [`syncer/SyncFilePairSyncer.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncFilePairSyncer.kt) |
| `Syncer/ISyncFilePairSyncer.cs` | `SyncFilePairCollectionCompareResult` | [`syncer/SyncFilePairCollectionCompareResult.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncFilePairCollectionCompareResult.kt) |
| `Syncer/LocalSyncer.cs` | `LocalSyncer` | [`syncer/LocalSyncer.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/LocalSyncer.kt) |
| `Syncer/ParallelSyncFilePairSyncer.cs` | `ParallelSyncFilePairSyncer` | [`syncer/ParallelSyncFilePairSyncer.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/ParallelSyncFilePairSyncer.kt) |
| `Syncer/SyncAction.cs` | `SyncAction` | [`syncer/SyncAction.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncAction.kt) |
| `Syncer/SyncCondition.cs` | `SyncCondition` | [`syncer/SyncCondition.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncCondition.kt) |
| `Syncer/SyncContext.cs` | `SyncContext` | [`syncer/SyncContext.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncContext.kt) |
| `Syncer/SyncFileCollectionSyncer.cs` | `SyncFileCollectionSyncer` | [`syncer/SyncFileCollectionSyncer.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncFileCollectionSyncer.kt) |
| `Syncer/SyncFileCollectionSyncer.cs` | `SyncFileCollectionComparerResult` | [`syncer/SyncFileCollectionComparerResult.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncFileCollectionComparerResult.kt) |
| `Syncer/SyncPathComparer.cs` | `SyncPathComparer` | [`syncer/SyncPathComparer.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncPathComparer.kt) |
| `Syncer/SyncPathComparer.cs` | `SyncFilePathCompareResult` | [`syncer/SyncFilePathCompareResult.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncFilePathCompareResult.kt) |
| `Syncer/SyncRule.cs` | `SyncRule` | [`syncer/SyncRule.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncRule.kt) |
| `Syncer/SyncRuleEvaluator.cs` | `SyncRuleEvaluator` | [`syncer/SyncRuleEvaluator.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncRuleEvaluator.kt) |
| `Syncer/SyncerOptions.cs` | `SyncerOptions` | [`syncer/SyncerOptions.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/syncer/SyncerOptions.kt) |
| `Versions/IVersionManager.cs` | `IVersionManager` | [`versions/VersionManager.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/versions/VersionManager.kt) |
| `Versions/NullVersionManager.cs` | `NullVersionManager` | [`versions/NullVersionManager.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/versions/NullVersionManager.kt) |
| `Versions/VersionManager.cs` | `VersionManager` | [`versions/FileVersionManager.kt`](../fishsync-core/src/main/kotlin/io/github/alphabs/fishsync/core/versions/FileVersionManager.kt) |

Kotlin/JVM 대응:

- Task/ValueTask 및 CancellationToken은 suspend 함수와 호출 코루틴의 취소 문맥으로 대응한다.
- .NET Stream은 InputStream/OutputStream으로 분리한다. 취소는 버퍼 작업 사이에서 확인하며 blocking I/O를 강제로 중단하지 않는다.
- glob은 사용자 결정에 따라 내부 GlobPattern의 *·?·독립된 ** 문법으로 구현한다. DotNet.Glob의 세부 동작은 복제하지 않는다.
- 경로의 대소문자 비교는 Kotlin 표준 API를 사용한다. .NET OrdinalIgnoreCase 보정은 유지하지 않는다.
- IProgress<T>는 타입화된 Kotlin 콜백으로 대응한다. 파일·바이트 진행률 객체와 집계 역할은 유지한다.
- .NET init-only 메타데이터는 Kotlin 생성자의 val 속성으로 대응한다.
- IEnumerable 탐색과 추가 파일 쌍 생성은 지연 실행한다. Collection.Count는 Kotlin size로 대응한다.
- private 보조 구현과 .NET 런타임 타입 자체를 복제하는 대신 동일한 역할과 관찰 가능한 계약을 유지한다.
