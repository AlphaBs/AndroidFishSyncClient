# 구현 현황

작성일: 2026-10-15 (Asia/Seoul)

## 이번 구현

- Kotlin 2.1.20, AGP 8.9.2, Gradle 8.11.1, JDK 17, Android minSdk 26 / compileSdk 35.
- `fishsync-core`, `fishsync-client`, `fishsync-android`, `sample-app`의 빌드 구조와 CI.
- 상대 경로 검증, 중복·파일/디렉터리 충돌·루트 내부 심볼릭 링크 거부.
- MD5/SHA1, 체크섬 메타데이터 검증, 세 가지 비교 방식, 순서가 있는 네 동기화 규칙과 버전 조건.
- 비교 결과와 상태 지문. 미리보기 뒤 원본 메타데이터 또는 로컬 내용이 달라지면 실행 거부.
- 임시 파일 검증 후 적용, 전송 실패 시 삭제 생략, 같은 루트의 PULL/PUSH 직렬화.
- 서버 로그인·버킷 목록·버킷 정보·파일 목록, PULL과 서버 HTTP 작업을 처리하는 PUSH.
- 서버 API 토큰을 외부 전송 URL로 자동 전달하지 않음. 서명 URL과 명시된 전송 헤더 보존.
- 앱 전용 폴더와 SAF 저장소. 저장소 조회 실패는 빈 목록으로 취급하지 않음.
- 폴더 선택, 접근 권한 유지, 비교, 명시적 실행 확인, 진행률·취소를 제공하는 샘플 앱.
- JVM·HTTP 계약 테스트와 테스트 DocumentsProvider를 사용하는 Android 계측 테스트.
- 실제 계정 없이 테스트할 수 있는 메모리 기반 모의 서버.

## 원본 호환성 검증

`tools/contract-reference`가 원본 .NET 라이브러리를 호출해 `fixtures/comparison-contract.json`의 기대 결과를 생성한다. Kotlin의 ReferenceContractTest가 같은 입력을 비교한다. 현재 9개 사례는 FullSync, UpdateOnly, InstallOnly, 규칙 우선순위, 체크섬 생략, 버전 조건·강제 적용, 대소문자, 재귀 glob이다.

이 검증에서 InstallOnly의 기존 파일은 동일 목록에도 포함하지 않는 원본 동작을 확인하고 맞췄다. 전체 원본 테스트를 이식했다는 의미는 아니다. `{…}` glob은 현재 지원하지 않고 명시적으로 거부한다.

기대 결과를 다시 생성하려면 .NET 10 SDK와 원본 저장소 체크아웃이 필요하다. 원본 참조 경로는 `-p:FishSyncOriginalProject=/absolute/path/src/FishSyncClient.csproj`로 바꿀 수 있다.

```bash
dotnet run --project tools/contract-reference -- fixtures/comparison-contract.json /tmp/fishsync-reference.json
# 생성 결과를 검토하고 fixtures/comparison-contract.json에 반영한다.
```

## 검증 기록

- JVM 테스트: 코어 13개, 공통 비교 계약 1개(내부 9개 사례), HTTP 클라이언트 8개.
- Android 15 에뮬레이터 계측 5개: 앱 전용 파일, SAF 생성·교체·삭제, 체크섬 실패 보호, 제공자 이름변경 실패 보호, 폴더 조회 실패 시 삭제 방지.
- debug/release AAR와 샘플 APK 빌드, R8 축소 APK, Android lint, Maven Local 배포 파일 생성. R8 APK의 기기 설치·앱 시작도 확인했다.
- 실제 DocumentsUI로 `Documents/FishSyncSmoke` 선택, 권한 부여, 앱 프로세스 재시작 후 선택 유지.
- 샘플 앱에서 로컬 모의 서버 로그인 → 버킷 선택 → SAF PULL → 파일 내용 확인 → 파일 수정 → PUSH → 서버 내용 확인.
- 운영 서버, 실제 사용자 계정 및 운영 버킷은 사용하지 않았다.

## 현재 보장과 제한

- 첫 구현의 전송은 직렬 처리한다. 병렬 처리 수 설정과 대규모 폴더 성능 최적화는 남아 있다.
- 코어의 비교/파일 추상화는 Kotlin용 API로 재구성했다. .NET의 모든 comparer와 확장 지점을 일대일 이식한 상태는 아니다.
- 의존 버킷은 현재 명시적으로 거부한다. 재귀 조회·우선순위 병합은 후속 작업이다.
- 현재 서버 HTTP 업로드 작업의 PUT/POST를 지원한다. 다른 작업 타입은 실행하지 않고 오류를 반환한다.
- 버킷 제한 정보는 `bucketInfo`로 조회할 수 있으나 UI 한도 표시·사전 제한 검증은 아직 없으며 서버가 최종 검증한다.
- PULL은 파일별 적용이다. 실패 전 이미 적용된 파일은 남을 수 있다. SyncFailure는 적용·삭제 목록을 제공한다. 취소도 전체 롤백을 뜻하지 않는다.
- SAF는 원자적 교체를 보장하지 않는다. 기존 파일을 `.fishsync-backup-*`으로 보존한 뒤 교체하고 실패하면 복구한다. 복구 실패 시 백업 이름을 오류에 남긴다.
- `.fishsync-*`는 SAF의 내부 작업 이름으로 예약한다. 프로세스 종료 시 남은 백업과 임시 문서의 자동 정리는 아직 하지 않는다. 원본 보호를 위해 백업을 임의 삭제하지 않는다.
- 제공자의 파일 조회·이름 변경·크기 보고 동작 차이는 추가 검증이 필요하다. 현재 Android 15와 테스트 제공자, 시스템 외부 저장소 제공자를 검증했다.
- 자동 예약·백그라운드 지속 실행·중단 지점부터 전송 재개·양방향 충돌 병합은 포함하지 않는다.
- R8 적용 샘플은 설치 검증을 위한 debug 키로 서명한다. 배포용 앱이나 서명 설정이 아니다.

## 다음 작업

1. 최저 지원 API 26과 실제 단말에서 SAF·권한 취소·회전·프로세스 종료 시나리오 검증.
2. 느린 제공자·큰 파일 목록 성능과 제한된 병렬 전송.
3. 의존 버킷, 서버 제한·오류 세부정보, 갱신된 전송 URL의 복구 정책.
4. 남은 원본 경계 사례와 비교기 API 확장.
5. 지정 테스트 서버에서의 호환성 검증과 공개 Maven 배포.

출시 준비 완료로 간주하지 않는다. 최초 구현과 반복 검증을 위한 기반이며 후속 검증은 계획의 완료 조건을 따른다.
