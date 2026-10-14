# AndroidFishSyncClient

[FishSyncClient](https://github.com/AlphaBs/FishSyncClient)의 동기화 동작과 FishSyncServer 연동을 Kotlin으로 구현하는 Android용 라이브러리입니다. 현재는 개발용 첫 구현이며 지원 범위와 검증 기록은 [구현 현황](docs/implementation-status.md)에 정리했습니다.

- 파일 목록·MD5/SHA1 비교와 순서가 있는 동기화 규칙
- 서버 로그인, 버킷 조회, PULL/PUSH, 진행률 및 취소
- 일반 파일과 시스템 폴더 선택기(SAF)로 선택한 폴더
- 변경 목록을 확인한 뒤 실행하는 Android 테스트 앱

## 빌드

JDK 17과 Android SDK 35 / Build Tools 35.0.0이 필요합니다. `ANDROID_HOME`을 설정하거나 `local.properties`에 `sdk.dir`을 지정하세요. 로컬 경로와 비밀 정보는 커밋하지 않습니다.

```bash
./gradlew :sample-app:assembleDebug
./gradlew :sample-app:installDebug
```

변경 검증과 테스트는 GitHub Actions에 위임합니다. CI에서 JVM 테스트, Android SAF 계측 테스트, Debug/Release 빌드와 lint를 실행합니다.

이 작업 환경에서는 프로젝트 내부 JDK를 사용하는 `bash scripts/gradle.sh`도 제공합니다. 다른 JDK를 지정하려면 `FISHSYNC_JAVA_HOME`을 설정하세요. VS Code의 Run Task에서도 검증·설치·계측 테스트·모의 서버를 실행할 수 있습니다.

## 코드 스타일

읽기 쉬운 코드를 우선하며 주석은 한글을 선호합니다. 문장과 제어 흐름을 나누고, 메서드 체이닝은 한 줄에 한 메서드씩 작성합니다. 긴 인자 목록은 줄바꿈하며 복잡한 조건은 `if`/`else`나 별도 메서드로 풉니다. 논리적인 작업 단계 사이에는 공백 라인을 둡니다.

`.editorconfig`와 고정 버전 ktlint로 Kotlin 코드와 Gradle 스크립트를 검사합니다. CI에서도 같은 검사를 실행합니다.

```bash
./gradlew formatKotlin
./gradlew checkKotlinStyle
```

## 샘플 앱

서버 API URL(`/api` 포함)과 로그인 정보를 입력하고 버킷을 선택합니다. 앱 전용 폴더 또는 시스템 폴더 선택기로 동기화 대상 폴더를 고릅니다. PULL/PUSH 방향을 정하고 **변경 목록 비교 → 동기화 실행 → 확인** 순서로 진행하세요.

**FullSync는 대상에만 있는 파일을 삭제합니다.** PULL의 대상은 로컬 폴더이며 PUSH의 대상은 서버 버킷입니다. 테스트 버킷과 테스트 폴더에서 먼저 확인하세요. 계정 토큰은 메모리에만 보관하며 폴더 URI와 접근 권한은 앱 재시작 후에도 유지합니다.

Android는 다른 앱의 `Android/data` 폴더 등 일부 위치 선택을 제한합니다. SAF 제공자에 따라 이름 변경이 지원되지 않으면 안전한 파일 교체를 거부합니다. SAF의 교체는 원자적이지 않으며 원본 백업을 통해 복구를 시도합니다.

## 로컬 모의 서버

운영 계정과 파일을 사용하지 않고 전체 흐름을 테스트할 수 있습니다.

```bash
python3 tools/mock-server.py
```

에뮬레이터에서 서버 URL은 `http://10.0.2.2:18080/api`입니다. 사용자 이름과 비밀번호는 임의 값으로 로그인할 수 있습니다. `test-bucket`에 `hello.txt`가 있으며 수정사항은 서버 메모리에만 반영됩니다. 이 서버는 인증·영속 저장을 구현하지 않은 개발용 도구이며 로컬 루프백에만 바인딩합니다. HTTP 접근은 샘플 **debug 빌드에서만** 허용합니다.

## 라이브러리 모듈

| 모듈 | 역할 |
| --- | --- |
| `fishsync-core` | 경로·체크섬·규칙·비교·안전한 로컬 적용 |
| `fishsync-client` | FishSyncServer API와 스트리밍 전송 |
| `fishsync-android` | 앱 전용 저장소·SAF 어댑터 |
| `sample-app` | 로그인·버킷·폴더 선택과 PULL/PUSH 검증 |

```kotlin
import io.github.alphabs.fishsync.android.SafSyncStorage
import io.github.alphabs.fishsync.client.FishSyncClient

val storage = SafSyncStorage(context, treeUri) // 시스템 선택기로 받은 URI 권한을 먼저 유지
val client = FishSyncClient(serverUrl, tokenProvider = { sessionToken })
val plan = client.comparePull(bucketId, storage)
// 사용자 확인 후 실행. 미리보기 이후 내용이 달라지면 다시 비교해야 합니다.
val result = client.pull(bucketId, storage, expectedPlan = plan)
```

`client.progress`는 전송 이벤트 Flow입니다. 수집하지 않아도 실행이 막히지 않습니다. `pull`/`push`는 suspend 함수이고 취소는 호출 코루틴으로 전달합니다. 일반 파일에는 `FileSyncStorage`를 사용합니다. `push`에는 동기화 루트 밖의 앱 전용 임시 폴더를 전달하세요.

개발 중 다른 앱에서 사용하려면 세 라이브러리 모듈의 `publishToMavenLocal`을 실행하고 Maven 메타데이터와 함께 참조할 수 있습니다. 공개 Maven 배포는 아직 설정하지 않았습니다.

## 문서

- [구현 계획](docs/implementation-plan.md)
- [구현 현황·검증·남은 작업](docs/implementation-status.md)
- [core 객체 구조와 확장](docs/core-architecture.md)
- [원본 core 구조·동작 차이 감사](docs/upstream-core-audit.md)
- [원본 비교 계약](fixtures/comparison-contract.json)

## 라이선스

MIT. 원본 FishSyncClient의 저작권과 라이선스 고지를 유지합니다.
