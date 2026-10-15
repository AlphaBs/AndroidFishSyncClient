# Android에서 달라져야 하는 경계

원본의 파일 객체·비교기·규칙·동기화·버전 계약은 core에서 유지한다. Android 저장소 접근 방식은 fishsync-android에 둔다. storage는 저장소 접근을 원본 동기화기에 연결하는 어댑터다.

| 경계 | 원본과 공통인 부분 | Android/JVM 대응 |
| --- | --- | --- |
| 상대 경로 | RootedPath, PathOptions, `.`·중복 구분자 정규화, `..` 거부 | SAF 문서 안에서도 논리 상대 경로로 유지한다. URI를 절대 파일 경로로 해석하지 않는다. |
| 파일 시스템 루트 | 원본 LocalPathGuard가 루트와 조상·하위 경로 링크를 거부 | 신뢰하는 Context.filesDir의 Android 경로 별칭만 AndroidStorage.appLocalSyncer에서 canonicalPath로 해석한다. 임의 경로나 URI를 canonicalize해 검사 우회에 사용하지 않는다. |
| URI·권한 | 파일의 읽기·쓰기 능력과 실패 전파 | SafSyncFile은 treeUri를 별도로 보관하고 ContentResolver와 DocumentsContract를 사용한다. 선택한 트리의 읽기·쓰기 권한 유지와 재확인이 필요하다. |
| 경로 대소문자 | PathOptions.caseInsensitive 기본값 true 유지 | Android 파일 시스템이 대소문자를 구분한다는 이유로 동기화 정책을 자동 변경하지 않는다. Kotlin 표준 String.CASE_INSENSITIVE_ORDER로 비교한다. |
| 파일 크기 | 메타데이터가 없으면 비교기 정책에 따라 오류 처리 | 문서 제공자의 크기는 미상일 수 있다. 크기 전용 비교에서는 명확한 FileComparerException을 반환하며 0으로 꾸미지 않는다. 체크섬 비교와 스트리밍은 계속 사용할 수 있다. |
| 취소 | 취소 전파 및 진행 중 작업을 관찰한 뒤 반환 | 버퍼 작업 사이에서 코루틴 취소를 확인한다. 읽기·쓰기를 강제로 중단하지 않으므로 blocking I/O가 반환될 때까지 취소 완료가 지연될 수 있다. |
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

StorageSyncer도 원본 SyncFileCollectionSyncer를 상속한다. 저장소 목록과 직접 읽기·쓰기만 연결하며 같은 규칙의 비교기로 전송 후 검증한다. 임시 교체·별도 검증·자동 삭제·부분 실패 결과는 제공하지 않는다. 삭제는 deleteFiles를 호출하는 코드에서 명시적으로 실행한다.

SafSyncFile과 SafSyncStorage는 모두 SafFileAccess의 문서 생성·직접 쓰기 스트림을 사용한다. `.fishsync-*` 이름 예약이나 백업 복구 정책은 없다. 제공자가 rename을 지원하지 않아도 직접 쓰기는 가능하다. 권한 조회 실패를 빈 폴더로 처리하지 않는다. 스트림 열기를 강제로 취소하지 않으며 SafSyncFile에서 전달받을 호출자가 없어진 경우에는 열린 자원을 닫는다.

## 포팅 시 복잡해진 부분

JVM에 대응 기능이 없는 경우와 비슷한 기능의 세부 규칙이 다른 경우를 구분한다.

| 구현 | 필요했던 이유 | 판단 |
| --- | --- | --- |
| DotNetWhitespace | Kotlin trim과 .NET String.Trim의 공백 집합이 다르다. | 버전 문자열의 원본 결과를 유지하기 위한 작은 보정이다. |
| 협력적 스트림 취소 | 일반 Java InputStream/OutputStream에는 CancellationToken을 받는 읽기·쓰기 API가 없다. | 강제 취소 연결은 제거했다. 읽기·쓰기 반환 후 취소를 확인하며 진행 중인 blocking I/O는 기다린다. |
| ConcurrentByteProgressAggregator | 원본 ThreadLocal 집계를 그대로 쓰면 스레드를 이동하는 코루틴의 수명과 맞지 않는다. | 원자적 합계로 단순화했다. ThreadLocal 자체를 흉내 내지 않는다. |
| NativePaths | Java File은 마지막 구분자를 없애 `file/`을 `file`로 다룰 수 있다. | 일반 파일을 잘못 읽거나 덮어쓰거나 삭제하지 않도록 필요한 파일 의미 보정이다. |

Task/ValueTask, IProgress, Stream을 별도 .NET 모방 계층으로 복제하지 않는다. suspend, 타입화된 콜백, InputStream/OutputStream을 사용한다. 파일 제공자의 권한·크기 미상·이름 변경 실패·취소는 런타임 API 변환보다 실제 오류와 자원 수명을 맞추는 작업이 까다롭다.

## 자체 glob 정책

내부 GlobPattern을 사용하며 외부 매처의 소스나 런타임 의존성을 포함하지 않는다. 경로 요소와 문자에 대한 동적 계획법으로 구현한다. 재귀나 백트래킹을 하지 않고 두 상태 행을 재사용한다.

- `*`: 한 경로 요소 안의 문자 0개 이상. `?`: Kotlin Char 하나(UTF-16 코드 단위 하나). `/`를 넘지 않는다.
- 독립된 경로 요소 `**`: 디렉터리 0개 이상. `**/*.txt`는 `file.txt`와 `a/b/file.txt`를 포함한다.
- `a**b`처럼 요소 안에 들어간 연속 별표는 일반 `*`와 같다. 디렉터리 경계를 넘지 않는다.
- `[ab]`, `[a-z]`, `{a,b}`는 문자 집합이나 선택 문법으로 해석하지 않는다. 별도 escape 문법도 두지 않는다.
- 역슬래시는 슬래시로 정규화한다. 중복·마지막 구분자는 경로 요소를 늘리지 않는다.
- 절대 패턴과 상대 경로는 일치하지 않는다. 빈 패턴은 빈 상대 경로와만 일치하며 `**`는 빈 상대 경로도 포함한다.
- 문자 비교는 Kotlin Char.equals(ignoreCase)를 사용한다. `İ`/`i` 및 `I`/`ı`도 기본 대소문자 무시 모드에서 일치한다.
- 최악 시간 복잡도는 패턴 길이와 경로 길이의 곱에 비례한다. 상태 메모리는 경로 요소 수와 가장 긴 파일명 길이에 비례한다. 선형 시간 매처는 아니다.

## 대소문자와 버전 공백 정책

경로 인덱스와 중복·존재 집합은 Kotlin 표준 String.CASE_INSENSITIVE_ORDER를 사용한다. 원본 이름을 대문자·소문자로 변환해 별도의 키를 만들지 않는다. 루트 경계는 startsWith(ignoreCase)를 사용한다. 따라서 `I`와 `ı`, `İ`와 `i`는 대소문자 무시 모드에서 같은 이름으로 취급한다. caseInsensitive=false이면 구분한다. 비교 결과의 원본 순서와 대상 파일 이름은 유지한다. 서버 PULL/PUSH에도 같은 표준 비교기를 사용한다.

DotNetWhitespace는 버전 문자열에만 유지한다. .NET String.Trim은 `U+0085`를 공백으로 제거하지만 Kotlin 기본 trim은 남긴다. 반대로 `U+001C`는 Kotlin에서 제거될 수 있지만 .NET에서는 남긴다. 일반 스페이스·탭·개행은 양쪽 모두 제거한다. Unicode 정규화(NFC/NFD)나 경로 공백 제거를 하는 기능은 아니다.

## ant-style-path-matcher 1.0.0 소스 검토

검토 대상은 Maven Central의 `io.github.azagniotov:ant-style-path-matcher:1.0.0` 소스 JAR다. AntPathMatcher.java와 AntPathMatcherArrays.java가 GitHub 커밋 `d66d51908bb7f66586f0bf5384ec348105c8fbac`의 소스와 바이트 단위로 일치함을 확인했다. 소스 제어 흐름을 직접 추적한 정적 검토이며 후보 매처를 로컬에서 실행하지 않았다. **이 후보는 채택하지 않는다.**

- `*` 처리의 접미사 탐색 루프가 경로 구분자에서 멈추지 않는다. 따라서 `*.txt`가 `dir/file.txt`와, `a*b`가 `a/x/b`와 일치하는 흐름이 된다. 두 구현 모두 같은 문제다.
- `?` 분기에 구분자 제외 검사가 없다. `a?b`가 `a/b`와 일치하는 흐름이 된다.
- ignoreCase는 문자의 차이가 32인지만 확인하며 영문자 범위 검사도 없다. `A.txt`와 `!.txt`가 같다고 판단한다. Unicode의 일반 대소문자 비교도 제공하지 않는다.
- 문자 단위 재귀와 wildcard의 반복 재시도를 사용한다. 긴 입력에는 호출 스택이 커지고 여러 wildcard 뒤에서 실패하는 입력에는 중복 탐색이 커질 수 있다. Arrays 구현은 substring 할당만 줄이며 재귀·재탐색 구조는 같다. 성능 수치는 측정하지 않았다.
- String 구현의 trimTokens=true 경로에서는 공백만 있는 비어 있지 않은 문자열을 처리할 때 charAt(length)에 도달할 수 있다. 기본 옵션에서는 비활성화된 분기다.

확인한 코드 위치:

- [AntPathMatcher.java:51–73](https://github.com/azagniotov/ant-style-path-matcher/blob/d66d51908bb7f66586f0bf5384ec348105c8fbac/src/main/java/io/github/azagniotov/matcher/AntPathMatcher.java#L51-L73): wildcard 및 경로 구분자 처리.
- [AntPathMatcher.java:105–122](https://github.com/azagniotov/ant-style-path-matcher/blob/d66d51908bb7f66586f0bf5384ec348105c8fbac/src/main/java/io/github/azagniotov/matcher/AntPathMatcher.java#L105-L122): 공백 및 대소문자 처리.
- [AntPathMatcherArrays.java:57–80](https://github.com/azagniotov/ant-style-path-matcher/blob/d66d51908bb7f66586f0bf5384ec348105c8fbac/src/main/java/io/github/azagniotov/matcher/AntPathMatcherArrays.java#L57-L80): 배열 구현에도 동일한 wildcard 처리.

검토한 후보와 Apache Ant 의존성은 사용하지 않는다. 자체 매처의 경로 구분자·문장부호·Unicode 정책과 작은 입력의 전수 대조, 긴 입력의 회귀 검증을 CI에서 수행한다.
