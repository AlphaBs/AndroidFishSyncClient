# 검증

- 테스트 실행은 CI에 위임한다. 로컬에서 단위 테스트나 Android 계측 테스트를 실행하지 않는다.
- 필요한 회귀 테스트는 코드로 추가하고 GitHub Actions 결과로 검증한다.
- 코드 포맷 정리와 정적 검토는 로컬에서 진행할 수 있다.

# core 구조

- 원본 FishSyncClient의 `Files`, `FileComparers`, `Syncer`, `Versions`, `Progress`, `Internals` 역할 구분을 유지한다.
- Kotlin에서는 `files`, `filecomparers`, `syncer`, `versions`, `progress`, `internals` 하위 패키지와 디렉터리를 일치시킨다.
- 인터페이스와 기본 구현은 별도 파일로 관리한다. core 루트에는 공통 경로·해시 도구만 둔다.
