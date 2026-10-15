# 검증

- 테스트 실행은 CI에 위임한다. 로컬에서 단위 테스트나 Android 계측 테스트를 실행하지 않는다.
- 필요한 회귀 테스트는 코드로 추가하고 GitHub Actions 결과로 검증한다.
- 코드 포맷 정리와 정적 검토는 로컬에서 진행할 수 있다.

# core 구조

- 원본 FishSyncClient의 `Files`, `FileComparers`, `Syncer`, `Versions`, `Progress`, `Internals` 역할 구분을 유지한다.
- Kotlin에서는 `files`, `filecomparers`, `syncer`, `versions`, `progress`, `internals` 하위 패키지와 디렉터리를 일치시킨다.
- 인터페이스와 기본 구현은 별도 파일로 관리한다. core 루트에는 공통 경로·해시 도구만 둔다.

- 원본 core의 인터페이스·구현 이름과 책임을 임의로 합치거나 축소하지 않는다.
- 원본에 없는 앱 저장소·미리보기·안전 적용 기능은 `storage` 확장에 두며 Android의 URI·권한·경로 별칭은 Android 모듈에서 처리한다.
- 비동기 API는 suspend·콜백·JVM 스트림으로 구현한다. 스트림 취소는 버퍼 작업 사이의 코루틴 취소 확인만 사용하며 강제 인터럽트·자동 닫기·SAF CancellationSignal 연결은 사용하지 않는다.
- glob 문법과 매칭 결과는 Apache Ant를 따른다. DotNet.Glob의 경계 동작과 문자 집합 문법을 복제하지 않는다.

- 경로 대소문자 비교는 Kotlin 표준 String.CASE_INSENSITIVE_ORDER와 ignoreCase 옵션을 사용한다. .NET OrdinalIgnoreCase의 별도 Unicode 보정을 추가하지 않는다.
