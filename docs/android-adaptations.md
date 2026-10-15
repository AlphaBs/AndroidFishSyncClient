# Android에서 달라져야 하는 경계

원본의 파일 객체·비교기·규칙·동기화·버전 계약은 core에서 유지한다. Android 저장소 접근 방식은 fishsync-android에 둔다. 원본에 없는 미리보기와 안전 적용 정책은 storage 확장으로 구분한다.

| 경계 | 원본과 공통인 부분 | Android/JVM 대응 |
| --- | --- | --- |
| 상대 경로 | RootedPath, PathOptions, `.`·중복 구분자 정규화, `..` 거부 | SAF 문서 안에서도 논리 상대 경로로 유지한다. URI를 절대 파일 경로로 해석하지 않는다. |
| 파일 시스템 루트 | 원본 LocalPathGuard가 루트와 조상·하위 경로 링크를 거부 | 신뢰하는 Context.filesDir의 Android 경로 별칭만 AndroidStorage.appLocalSyncer에서 canonicalPath로 해석한다. 임의 경로나 URI를 canonicalize해 검사 우회에 사용하지 않는다. |
| URI·권한 | 파일의 읽기·쓰기 능력과 실패 전파 | SafSyncFile은 treeUri를 별도로 보관하고 ContentResolver와 DocumentsContract를 사용한다. 선택한 트리의 읽기·쓰기 권한 유지와 재확인이 필요하다. |
| 경로 대소문자 | PathOptions.caseInsensitive 기본값 true 유지 | Android 파일 시스템이 대소문자를 구분한다는 이유로 동기화 정책을 자동 변경하지 않는다. .NET OrdinalIgnoreCase 규칙으로 비교한다. |
| 파일 크기 | 메타데이터가 없으면 비교기 정책에 따라 오류 처리 | 문서 제공자의 크기는 미상일 수 있다. 크기 전용 비교에서는 명확한 FileComparerException을 반환하며 0으로 꾸미지 않는다. 체크섬 비교와 스트리밍은 계속 사용할 수 있다. |
| 취소 | 취소 전파 및 진행 중 작업을 관찰한 뒤 반환 | .NET 비동기 스트림의 취소 토큰 대신 코루틴 취소에 인터럽트·스트림 닫기를 연결한다. 제공자가 닫기에도 반응하지 않는 경우 즉시 중단을 보장할 수 없다. |
| 진행률 | 파일·바이트 객체, 델타와 합계 집계 | SAF는 seek/position을 지원하지 않을 수 있으므로 읽은 바이트를 직접 센다. 코루틴이 스레드를 이동하므로 합계는 ThreadLocal 대신 원자적 값으로 관리한다. |
| 디스크 I/O | 비교·동기화 결과와 오류 계약 | SAF 고수준 진입점은 Dispatchers.IO에서 제공자 조회를 실행한다. |
| 삭제 범위 | 원본처럼 동기화와 삭제를 별도 호출 | LocalSyncer는 root를, SafSyncer.deleteSafFiles는 treeUri를 대조한다. 상대 경로가 같아도 다른 트리의 파일은 삭제하지 않는다. |
| 파일 교체 | 스트림 열기·쓰기·닫기 계약 | SAF에는 일반 파일 시스템 rename을 적용할 수 없다. 제공자의 생성·이름 변경·삭제 API를 사용하며 원자성을 보장하지 않는다. |

## 원본 API를 사용하는 진입점

- 일반 JVM 파일 경로: `LocalSyncer(root, pathOptions, pairSyncer)`.
- Android 앱 전용 폴더: `AndroidStorage.appLocalSyncer(context)`.
- 사용자가 선택한 SAF 폴더: `SafSyncer(context, treeUri)`와 `SafSyncFile`.

SafSyncFile의 path는 root 없는 RootedPath다. 같은 파일의 treeUri는 별도 프로퍼티로 유지한다. 원본의 File.Exists·FileInfo.Length는 LocalSyncFile의 재정의 가능한 존재·크기 경계로 옮겼고 일반 파일에서는 같은 결과를 유지한다. 이 파일은 로컬 파일 스트림·존재·크기 계약을 구현하므로 원본 LocalFileChecksumComparer와 LocalFileSizeComparer를 그대로 사용할 수 있다. 제공자에서 크기를 알 수 없을 때의 실패는 별도로 처리한다.

## Android 필수 차이와 앱의 추가 정책 구분

원본 LocalSyncer는 직접 대상에 쓰고 해당 규칙의 비교기로 전송 후 검증한다. 자동 삭제를 하지 않으므로 결과의 deletedFiles를 명시적으로 삭제해야 한다. 이 core 계약을 유지한다.

FishSyncClient·샘플 앱이 제공하는 임시 파일 검증 후 적용, 원본 백업 복구, 미리보기 fingerprint, 적용 후 삭제, 같은 루트 잠금, 부분 적용 결과와 전송 스냅샷은 storage 확장 정책이다. 이 기능들이 전부 Android 때문에 필수인 것은 아니다. 원본 core의 동작을 바꾸는 대신 별도 계층에서 제공한다.

SafSyncFile은 제공자의 문서 생성·직접 쓰기 스트림을 사용한다. 백업이나 내부 파일 이름 예약 정책을 적용하지 않으며 원본처럼 전송 후 비교기로 검증한다. 스트림 열기에는 CancellationSignal을 연결한다. 체크섬 실패 전 원본 파일까지 보호하려면 샘플 앱이 사용하는 StorageSyncer·SafSyncStorage 경로를 사용한다.

SAF의 `.fishsync-*` 임시·백업 이름 예약, 복구 정책과 프로세스 종료 후 잔여 문서 관리 역시 storage 확장 정책이다. 권한 조회 실패를 빈 폴더로 처리하지 않는다.

## 포팅 시 복잡해진 부분

JVM에 대응 기능이 없는 경우와 비슷한 기능의 세부 규칙이 다른 경우를 구분한다.

| 구현 | 필요했던 이유 | 판단 |
| --- | --- | --- |
| GlobPattern | JVM PathMatcher의 glob과 DotNet.Glob 3.1.3의 토큰·매칭 규칙이 다르다. 잘못된 패턴과 경계 동작까지 맞추려고 약 250줄을 옮겼다. | 가장 큰 호환성 비용이다. 원본 패턴과 결과를 유지한다는 요구 때문에 보존한다. 패턴 문법 변경을 허용하면 표준/외부 라이브러리로 줄일 수 있지만 결과도 달라진다. |
| OrdinalCase | Java의 대소문자 무시 비교와 .NET OrdinalIgnoreCase는 점 없는 i 등 일부 Unicode 문자의 처리가 다르다. | 비교 기능 자체는 JVM에도 있다. 호환성 보정이며 모든 Unicode 버전 간 완전 일치를 보장하는 구현은 아니다. |
| DotNetWhitespace | Kotlin trim과 .NET String.Trim의 공백 집합이 다르다. | 버전 문자열의 원본 결과를 유지하기 위한 작은 보정이다. |
| StreamCancellation | 일반 Java InputStream/OutputStream에는 CancellationToken을 받는 읽기·쓰기 API가 없다. 코루틴 취소만으로 blocking I/O가 멈추지 않는다. | 필요한 런타임 대응이다. 인터럽트와 close를 연결해도 제공자가 둘 다 무시하면 즉시 중단할 수 없다. |
| ConcurrentByteProgressAggregator | 원본 ThreadLocal 집계를 그대로 쓰면 스레드를 이동하는 코루틴의 수명과 맞지 않는다. | 원자적 합계로 단순화했다. ThreadLocal 자체를 흉내 내지 않는다. |
| NativePaths | Java File은 마지막 구분자를 없애 `file/`을 `file`로 다룰 수 있다. | 일반 파일을 잘못 읽거나 덮어쓰거나 삭제하지 않도록 필요한 파일 의미 보정이다. |

Task/ValueTask, IProgress, Stream을 별도 .NET 모방 계층으로 복제하지 않는다. suspend, 타입화된 콜백, InputStream/OutputStream을 사용한다. 파일 제공자의 권한·크기 미상·이름 변경 실패·취소는 런타임 API 변환보다 실제 오류와 자원 수명을 맞추는 작업이 까다롭다.
