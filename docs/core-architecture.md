# core 객체 구조

원본 FishSyncClient/src의 최상위 객체와 인터페이스 이름·책임을 유지한다. 전체 원본 타입과 Kotlin 소스 파일의 대응은 [타입 대응표](upstream-type-map.md)에 기록한다.

| 패키지 | 원본 역할 |
| --- | --- |
| core 루트 | RootedPath, PathOptions, PathHelper, ChecksumAlgorithms, ChecksumAlgorithmNames, HashHelper |
| files | 추상 SyncFile, LocalSyncFile, ReadableHttpSyncFile, VirtualSyncFile, 파일 메타데이터·쌍·컬렉션 |
| filecomparers | IFileComparer, IFileComparerFactory, 로컬·메타데이터 비교기, glob 비교기와 오류 정책 |
| syncer | SyncPathComparer, 규칙·옵션·결과, ISyncFilePairSyncer, ParallelSyncFilePairSyncer, SyncFileCollectionSyncer, 상속형 LocalSyncer |
| progress | ByteProgress, 파일·바이트 이벤트, 순차·동시 집계와 파일 상태 컬렉션 |
| versions | IVersionManager, 파일 구현 VersionManager, NullVersionManager |
| internals | 원본 경로·스트림·진행률 보조 객체 및 JVM 취소·glob 런타임 대응 |
| storage | 앱의 저장소·미리보기·안전 적용 확장. 원본 API와 구분한다. |

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

규칙마다 IFileComparer를 전달한다. CompositeFileComparerWithGlob은 처음 일치하는 패턴의 비교기 하나를 선택한다. 메타데이터 비교기는 오류 처리 모드를 지원하며 로컬 비교기와 구분한다. 원본처럼 전송 후 같은 규칙의 비교기로 검증한다.

추상 SyncFile은 경로·불변 메타데이터·읽기·쓰기·복사 계약을 가진다. 일반 파일·HTTP·가상 파일과 파일 쌍·컬렉션은 공통 타입을 사용한다. 결과는 경로 문자열만이 아니라 파일 객체와 파일 쌍을 반환한다.

기본 병렬 전송기는 프로세서 수 기준 1~4개를 사용하며 병렬도를 지정할 수 있다. 큐·비교·동기화 이벤트와 바이트 델타를 제공하고 파일 상태·합계 집계 객체를 유지한다. suspend 함수와 호출 코루틴의 취소 문맥이 Task/ValueTask·CancellationToken에 대응한다.

VersionManager는 IVersionManager의 비동기 파일 구현이다. UTF-8 128바이트 제한·trim·직접 쓰기를 유지하며 NullVersionManager는 항상 새 버전으로 판단한다.

## 앱 저장소 확장

서버 클라이언트와 샘플 앱은 storage.StorageSyncer를 사용한다. 이 계층은 SourceFile·저장소를 원본 SyncFile 모델로 변환해 원본 컬렉션 비교기를 호출하고, 미리보기·임시 적용·실패 시 삭제 생략을 추가한다. 규칙은 원본 SyncRule과 IFileComparer를 직접 사용한다. 추가 무결성 해시는 수정·삭제 대상에만 수행하므로 제외·크기 비교 파일의 내용을 강제로 읽지 않는다.

[Android 대응 경계](android-adaptations.md)에 일반 파일·앱 전용 폴더·SAF 진입점과 필수 차이, 선택적인 안전 정책을 구분했다.

## 검증

테스트는 CI에서만 실행한다. 고정된 원본 소스를 체크아웃하고 .NET 비교 결과와 glob 결과를 생성해 Kotlin과 대조한다. 객체·비교 정책·병렬 실패·취소·진행률·버전 및 HTTP/SAF 계약을 테스트한다. Android 계측은 API 26과 35에서 실행하며 Debug/Release 빌드와 Android lint도 실행한다. 타입 대응표의 파일 존재와 전체 동작의 동일성 보장은 구분한다.
