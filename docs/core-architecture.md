# core 객체 구조

원본 FishSyncClient의 역할 구분을 Kotlin 인터페이스와 기본 구현으로 옮긴다. 모든 공개 타입은 기존 `io.github.alphabs.fishsync.core` 패키지에 두며 인터페이스와 구현체는 별도 파일로 관리한다.

| 원본 역할 | Kotlin 계약 | 기본 구현과 책임 |
| --- | --- | --- |
| `IFileComparer` | `FileComparer` | `FileSizeComparer`, `FileChecksumComparer`, `CompositeFileComparer` |
| `IFileComparerFactory` | `FileComparerFactory` | `DefaultFileComparerFactory`: 규칙의 `Comparison`으로 비교기 선택 |
| `SyncFilePair` | `SyncFilePair` | 원본 파일과 적용 대상 메타데이터의 쌍 |
| `ISyncFilePairSyncer` | `SyncFilePairSyncer` | `DefaultSyncFilePairSyncer`: 단일 파일 전송·검증·적용 |
| `IVersionManager` | `VersionManager` | `VersionState`: 파일에 버전 저장, `NullVersionManager`: 저장 없이 항상 새 버전 취급 |
| 파일 저장소 | `ReadableSyncStorage`, `WritableSyncStorage` | `FileSyncStorage`, Android의 `SafSyncStorage` |
| 컬렉션 동기화 조율 | `SyncEngine` | 경로·규칙 검증, 계획 생성, 저장소 잠금, 적용·삭제 순서와 부분 결과 관리 |

## 조립과 확장

```kotlin
val engine = SyncEngine(
    comparerFactory = DefaultFileComparerFactory(),
    filePairSyncer = DefaultSyncFilePairSyncer(),
)
val client = FishSyncClient(serverUrl, engine = engine)
```

기본 인자 덕분에 기존 `SyncEngine()`과 `FishSyncClient(...)` 호출은 그대로 사용할 수 있다. 사용자 정의 비교기는 `FileComparerFactory`를 통해 주입한다. 비교 정책을 바꾸더라도 기본 전송기의 크기·체크섬 검증은 유지된다.

`compare`는 읽기 전용 저장소만 요구하며 `pull`은 쓰기 가능한 저장소를 요구한다. 기존 `SyncStorage`는 `WritableSyncStorage`를 상속하므로 기존 일반 파일·SAF 구현은 그대로 사용할 수 있다. JVM 메서드 서명은 바뀌므로 기존 바이너리 소비자는 다시 빌드해야 한다. 공개 배포 전 단계이며 바이너리 호환 브리지는 추가하지 않는다.

## 전송 구현의 계약

전송기는 임시 쓰기 → 크기·체크섬 검증 → 대상 변경 검사 → 취소 검사 → commit 순서로 적용한다. commit 직후 `onApplied`를 호출하고 자원을 정리한다. 정리 중 실패하더라도 엔진의 부분 적용 목록에 이미 반영된 파일이 남는다. 전송 실패나 취소가 있으면 엔진은 삭제 단계에 진입하지 않는다.

기본 전송기는 순차 실행을 유지한다. 원본의 병렬 전송기를 옮기려면 적용 결과 집계와 취소·부분 실패 계약을 함께 설계해야 하므로 이번 구조 분리에는 포함하지 않는다. 버전 관리자는 명시적으로 사용하는 별도 정책이며 동기화 성공 시 자동으로 버전을 갱신하지 않는다.

## 검증

테스트는 GitHub Actions에 위임한다. 기존 원본 비교 fixture와 HTTP 계약 테스트에 더해 읽기 전용 저장소, 비교기 주입, 전송기 주입, 자원 정리 실패 시 부분 적용 기록, 버전 관리자 정책을 확인한다. Android SAF 계측 테스트도 CI 에뮬레이터에서 실행한다.
