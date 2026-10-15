# 구현 현황

원본 FishSyncClient/src의 최상위 public/internal 타입 54개를 이름·역할·패키지별로 대응한다. [타입 대응표](upstream-type-map.md), [core 구조](core-architecture.md), [Android 대응 경계와 포팅 비용](android-adaptations.md)을 기준으로 한다.

## 현재 구성

- Kotlin 2.1.20, AGP 8.9.2, Gradle 8.11.1, JDK 17, Android minSdk 26 / compileSdk 35.
- core는 원본 파일·비교기·규칙·진행률·버전 모델을 제공한다. 파일·체크섬·규칙은 SyncFile·SyncFileChecksum·SyncRule·IFileComparer로 통일한다.
- storage 확장은 미리보기, 임시 파일 검증 후 적용, 실패 시 삭제 생략, 같은 루트 잠금과 부분 적용 결과를 제공한다. 서버 클라이언트와 샘플 앱에서 사용한다.
- client는 로그인·버킷 조회·PULL/PUSH와 전송 진행률·취소를 제공한다.
- android는 앱 전용 파일과 SAF 문서 트리 접근을 제공한다. 샘플 앱에서 폴더 선택과 영구 URI 권한 유지를 처리한다.
- 모의 서버는 운영 계정 없이 로그인·PULL/PUSH를 확인하기 위한 개발 도구다.

## CI 검증

로컬에서 테스트를 실행하지 않는다. [GitHub Actions](https://github.com/AlphaBs/AndroidFishSyncClient/actions/workflows/build.yml)에서 다음을 검증한다.

- 고정한 원본 .NET 소스로 비교 계약 9개 사례, glob 2,312개 사례, 버전 공백 8개 사례를 생성해 Kotlin 결과와 대조한다.
- JVM 단위·HTTP·계약 테스트와 취소·병렬 실패·미리보기 보호를 검증한다.
- Android API 26과 35에서 앱 전용 경로, SAF 생성·교체·삭제, 원본 비교기 연결, 내부 이름 정책 분리와 제공자 실패를 계측한다.
- Kotlin 스타일, Debug/Release 앱 빌드와 Android lint를 실행한다.

타입 파일의 존재와 전체 동작 동일성은 구분한다. 현재 테스트는 모든 원본 입력과 모든 Android 문서 제공자를 검증했다는 의미가 아니다.

## 제한과 남은 작업

- 원본 병렬 전송은 기본 CPU 수 기준 1~4개다. storage 안전 적용 확장은 직렬 처리하며 큰 SAF 트리의 성능 검증은 남아 있다.
- 의존 버킷은 명시적으로 거부한다. 재귀 병합과 우선순위 처리는 후속 작업이다.
- 서버 HTTP 업로드 작업은 PUT/POST를 지원한다. 다른 타입은 오류로 반환한다.
- 버킷 제한은 조회할 수 있으나 UI 한도 표시와 사전 제한 검증은 아직 없다.
- PULL은 파일별 적용이며 전체 롤백을 보장하지 않는다. 취소 전에 적용한 파일은 남을 수 있다.
- SafSyncFile은 직접 쓰기와 원본 검증을 사용한다. SafSyncStorage는 백업 복구를 추가하지만 제공자의 교체 원자성을 보장하지 않는다.
- SafSyncStorage의 `.fishsync-*` 이름은 내부 작업용으로 예약한다. 프로세스 종료 후 잔여 백업·임시 문서의 자동 정리는 아직 없다.
- 실제 제공자별 권한 철회·느린 I/O·이름 변경·크기 미상·프로세스 종료 시나리오와 큰 폴더 성능 검증이 남아 있다.
- 예약 실행·전송 재개·양방향 충돌 병합·공개 Maven 배포는 포함하지 않는다.
- 샘플 release 빌드는 개발용 debug 키를 사용한다.
