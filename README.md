# AndroidFishSyncClient

[FishSyncClient](https://github.com/AlphaBs/FishSyncClient)의 동기화 엔진과 FishSyncServer 연동을 Kotlin으로 구현하는 Android용 라이브러리 프로젝트입니다.

첫 버전은 서버 로그인, 버킷 조회, PULL/PUSH, 진행률 및 취소를 지원하는 것을 목표로 합니다. 테스트용 Android 앱에서 시스템 폴더 선택기(SAF)를 통해 동기화 대상 폴더를 선택할 수 있도록 설계합니다.

현재는 계획 단계이며 라이브러리 코드, Gradle 빌드 설정 및 테스트 앱은 아직 구현하지 않았습니다.

## 개발 계획

[포팅 및 구현 계획](docs/implementation-plan.md)을 참고하세요.

## 계획된 모듈

| 모듈 | 역할 |
| --- | --- |
| `fishsync-core` | Android에 의존하지 않는 파일 비교, 규칙 평가 및 동기화 엔진 |
| `fishsync-client` | FishSyncServer API와 다운로드·업로드 처리 |
| `fishsync-android` | 일반 파일·SAF 저장소 연결과 Android 진입 API |
| `sample-app` | 로그인, 버킷·폴더 선택, 비교, PULL/PUSH 및 취소 테스트 |

## 라이선스

MIT. 포팅하는 원본 코드의 저작권 및 라이선스 고지를 유지합니다.
