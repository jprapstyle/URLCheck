# URLCheck 로컬 변경 내역

최종 수정: 2026년 10월 30일, 한국 시간.

[English](LOCAL_CHANGES_EN.md)

원본 URLCheck와 비교해 로그 저장과 규칙·아이콘 재사용 방식을 개선하고, 반복 팝업 표시 전에 수행하는 작업을 줄였다. 팝업 표시 전 처리 부담을 줄이기 위해 로그 저장을 비동기 파일 기록으로 전환하고, 규칙·정규식·Hosts 조회·앱 아이콘을 캐시하며, 보조 앱 메뉴 생성 지연과 선택 이력 정렬의 중복 작업 감소를 적용했다. 고정 브라우저 선택과 화면 방향 설정도 추가했으며, 최신 원본 기능은 유지했다.

## 목차

- [소스 기준과 적용 범위](#소스-기준과-적용-범위)
- [로그와 캐시 개선](#로그와-캐시-개선)
- [브라우저 선택 기능](#브라우저-선택-기능)
- [화면 방향 설정](#화면-방향-설정)
- [반복 실행 최적화](#반복-실행-최적화)
- [빌드와 검증](#빌드와-검증)
- [폴드5 속도 측정](#폴드5-속도-측정)
- [재측정 방법](#재측정-방법)
- [후속 과제](#후속-과제)

## 소스 기준과 적용 범위

| 항목 | 기준 |
|---|---|
| 작업 저장소 | `C:/Users/enLioN/Personal_Project/URLCheck` |
| 사용자 GitHub 포크 | https://github.com/jprapstyle/URLCheck |
| 원본 프로젝트 | https://github.com/TrianguloY/URLCheck |
| 로컬 작업 시작 기준 | `0a4958d945ca0b86a169694dde20e53d0bbb2691` |
| 최신 소스 통합 기준 | `5886dad266f8d685ad2c4537bd36225185881a9b` |

통합 당시 사용자 포크와 원본 master는 같은 커밋이었다. 작업 시작 기준 이후 7개 커밋, 17개 파일 변경이 있었으며, 팝업 위치 설정과 Redirect 모듈이 추가됐다. 이 기능들은 그대로 유지했다. Redirect 모듈은 기본 비활성화이며, 호스트 치환 규칙과 수동·자동 적용, JSON·시각적 편집기를 제공한다.

기존 개선본에서 기존 파일 12개 수정과 소스·테스트 파일 5개 추가를 이식했다. 양쪽에서 수정한 `ClearUrlCatalog.java`는 3방향 병합했고, 영어 `strings.xml`은 문자열 키별로 양쪽 변경을 유지했다. 이후 화면 방향 설정과 반복 실행 최적화를 추가했다. 변경 사항은 사용자 GitHub 포크에 소스와 문서를 함께 커밋하는 대상으로 정리했다. 최종 업로드 이력은 Git 기록에서 확인한다.

## 로그와 캐시 개선

### 로그 저장

기존의 커지는 SharedPreferences 로그 문자열 대신 앱 내부 `url-log.txt` 파일에 기록한다. 단일 작업 스레드에서 순차 처리해 화면 스레드의 파일 작업과 큰 문자열 재생성 부담을 줄인다.

- 기존 로그는 `AtomicFile`을 이용해 파일로 옮긴 후 이전 설정 키를 제거한다.
- 미리보기는 최대 64KiB로 제한하며, 일부만 표시한 내용을 전체 로그로 잘못 덮어쓰지 않도록 처리한다.
- 로그 저장·초기화와 백업·복원은 공통 잠금을 사용한다.
- 백업·복원에서 로그 파일을 민감 데이터로 취급한다.
- 로그는 앱 내부에 저장되며, 이 변경으로 서버 전송 기능을 추가하지 않았다.

주요 파일: `LogStore.java`, `LogModule.java`, `BackupActivity.java`.

### 반복 계산 캐시

| 대상 | 변경 |
|---|---|
| ClearURL 규칙 | 파싱된 규칙 재사용, 규칙 변경 시 무효화 |
| ClearURL 정규식 | 최대 512개 LRU 캐시 |
| Hosts 조회 | 최대 16개 LRU 캐시 |
| 앱 아이콘 | Drawable ConstantState 최대 32개 LRU 캐시 |

주요 파일: `ClearUrlCatalog.java`, `ClearUrlModule.java`, `Hosts.java`, `IntentApp.java`.

## 브라우저 선택 기능

설정의 열기·공유 항목에 고정 브라우저 기능을 추가했다. URL 팝업의 열기 버튼을 길게 눌러 현재 브라우저 고정, 브라우저 변경, 고정 해제 등의 메뉴를 사용할 수 있다. 영어·한국어 문구를 제공한다.

학습된 선택 이력이 없을 때는 링크 전용 앱을 우선하고, 그다음 고정 브라우저를 기본 후보로 삼는다. 기존 `LastOpened` 선택 이력이 있으면 고정 브라우저 기능이 켜져 있어도 그 이력을 우선 반영한다. 예를 들어 쿠팡 링크에서 Choose Browser를 선택한 이력이 있으면 이후 선택에 반영된다.

기존 앱 간 선호 점수와 저장 키를 유지한다. 점수 범위는 -3~3이며 시간 만료 방식은 아니다. 도메인별 정렬 설정도 유지한다. 숨긴 앱, 호출 앱 제외, 거절 앱 제외 설정은 기존 필터에 따라 처리한다. 고정 기능 자체가 브라우저를 자동 실행하지는 않는다.

주요 파일: `BrowserPreference.java`, `OpenModule.java`, `IntentApp.java`, `config_open.xml`, 영어·한국어 `strings.xml`.

## 화면 방향 설정

일반 설정의 테마·팝업 크기 항목 근처에 화면 방향 선택을 추가했다. 글자와 콤보 박스는 테마 설정처럼 같은 줄에 배치하고, 설명은 아래에 표시한다.

| 선택 | 동작 | Android 요청 값 |
|---|---|---|
| 시스템 설정 따름 | 시스템 자동 회전·회전 잠금 설정을 따름 | `SCREEN_ORIENTATION_USER` |
| 세로 고정 | 기본값, 세로 방향 유지 | `SCREEN_ORIENTATION_PORTRAIT` |
| 가로 고정 | 가로 방향 유지 | `SCREEN_ORIENTATION_LANDSCAPE` |
| 항상 자동 회전 | 시스템 잠금과 관계없이 센서 방향을 따름 | `SCREEN_ORIENTATION_FULL_SENSOR` |

`screen_orientation` 설정에 선택을 저장한다. `URLCheckApplication`의 Activity 생성·재개 콜백에서 정책을 적용하므로 URL 팝업과 앱 내부 화면에 공통 적용된다. 기존 Manifest의 Activity별 세로 고정 선언은 제거했다. 영어·한국어를 지원한다.

큰 화면에서는 Android·제조사 정책에 따라 방향 고정 요청이 적용되지 않을 수 있다. 접힌 화면·펼친 화면과 모든 선택지의 실제 회전 동작은 별도 확인 대상이다.

주요 파일: `URLCheckApplication.java`, `OrientationSettings.java`, `SettingsActivity.java`, `AndroidManifest.xml`, `activity_settings.xml`, 영어·한국어 `strings.xml`.

## 반복 실행 최적화

사용자 요청에 따라 프로세스가 이미 실행 중일 때의 팝업 표시 속도를 최우선으로 두고, 완전한 최초 실행 최적화는 후순위로 정했다.

### 다른 앱으로 열기 메뉴 생성 지연

이전에는 팝업을 열 때마다 보조 앱 목록의 이름을 읽고 메뉴를 만들었다. 현재는 기본 선택 앱을 먼저 표시하고, 사용자가 다른 앱 목록을 펼칠 때 보조 메뉴를 생성한다. 앱 후보 조회와 숨김·거절·호출 앱 필터는 계속 수행한다. 앱 설치 상태를 오래 캐시하는 변경은 넣지 않았다.

변경 파일: `OpenModule.java`의 `updateSpinner()`와 `showList()`.

### 선택 이력 정렬의 중복 작업 감소

정렬 비교마다 하던 도메인 분석과 SharedPreferences 객체 조회를 정렬 호출당 한 번으로 줄였다. 컴포넌트 이름도 정렬 중 재사용한다. 기존 점수 키·안정 정렬·도메인 범위·선호 점수 해석을 유지했다.

변경 파일: `LastOpened.java`의 `sort()`.

### 애니메이션 설정

기존 애니메이션 설정은 `LayoutTransition`을 제어한다. OFF로 하면 내부 항목의 크기·위치·표시 변경 애니메이션과 초기 객체 생성 부담을 줄일 수 있지만 프로세스 생성이나 URL 분석을 생략하지는 않는다. 이번 변경에서는 사용자 애니메이션 설정과 동작을 유지했다. ON/OFF 속도 비교는 수행하지 않았다.

## 빌드와 검증

| 항목 | 결과 |
|---|---|
| Release APK | `3.5-enlion.1`, 빌드 성공, 미서명 |
| Alpha APK | `3.5-enlion.1-alpha.1`, 빌드 성공 |
| Evaluation 앱과 AndroidTest APK | 빌드 성공 |
| 폴드5 브라우저 우선순위 검사 | 40개 통과 |
| 최신 Release·Alpha Lint | 각각 오류 0건, 경고 133건 |
| 통합 직후 Lint | 오류 0건, 경고 153건 |
| 폴드5 설치 | 최신 반복 실행 개선과 화면 방향 UI 수정 포함, 업데이트 설치 완료 |

방향 고정 선언 제거로 관련 경고 20건이 사라졌다. 그 외 경고를 일괄 수정하거나 숨기지는 않았다. 40개 검사는 우선순위, 선택 이력, 도메인 범위, 대체 후보, 저장, 영어·한국어 설정을 확인하며 모든 앱 기능을 포괄하는 테스트는 아니다. 별도 Evaluation 패키지에서 실행한 후 테스트 패키지를 제거했다.

폴드5 속도 측정에 사용한 설치 패키지는 `com.trianguloy.urlchecker.alpha`, 버전은 `3.5-ALPHA-browser2`, versionCode는 47이었다. 이후 배포 식별을 위해 아래 버전 규칙을 적용했다. 새 버전은 아직 기기에 설치하지 않았다.

### 포크 버전 규칙

정식 버전은 `원본버전-enlion.수정번호`로 표기한다. 원본 버전은 통합한 원본을 따르고, 자체 배포마다 수정번호를 증가시킨다. 원본 3.6을 반영하면 `3.6-enlion.1`부터 시작한다. `versionCode`는 같은 패키지의 업데이트마다 증가시키며 원본 버전 변경 시에도 초기화하지 않는다.

| 빌드 | 이번 버전 | versionCode | 패키지 |
|---|---|---|---|
| release | `3.5-enlion.1` | 48 | `com.trianguloy.urlchecker` |
| alpha | `3.5-enlion.1-alpha.1` | 48 | `com.trianguloy.urlchecker.alpha` |
| evaluation | `3.5-enlion.1-test.1` | 48 | `com.trianguloy.urlchecker.test` |

각 빌드는 서로 다른 패키지이므로 이번에는 같은 코드 48을 사용한다. 다음 테스트 배포는 예를 들어 `3.5-enlion.1-alpha.2`와 코드 49로 증가시킨다. Alpha 패키지는 기존 이름을 유지하므로 같은 서명으로 기존 Alpha 앱을 업데이트할 수 있다.

Release는 난독화와 리소스 축소가 활성화된 빌드다. 현재 릴리스 키와 비밀번호가 설정되어 있지 않아 서명되지 않은 APK로 생성된다. 설치·배포하려면 릴리스 키로 서명해야 한다. 원본 배포본과 패키지명이 같아도 서명이 다르면 덮어쓰기 업데이트할 수 없다. 이번에는 서명 키 생성이나 GitHub Release 발행을 하지 않는다.

APK 위치:

- Release: `app/build/outputs/apk/release/URLCheck-3.5-enlion.1.apk` (미서명)
- Alpha: `app/build/outputs/apk/alpha/URLCheck-3.5-enlion.1-alpha.1-alpha.apk`

기존 속도 측정은 버전 변경 전 APK로 수행했다. 버전 표기 변경만으로 새로운 속도 측정 결과가 생기는 것은 아니다.

PowerShell 빌드 명령:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleRelease lintRelease assembleAlpha lintAlpha --offline --console=plain
.\gradlew.bat assembleEvaluation assembleEvaluationAndroidTest '-PcheckVariant=evaluation' '-PcheckRunner=com.trianguloy.urlchecker.BrowserChecks' --offline --console=plain
```

`--offline`은 필요한 의존성과 SDK가 이미 준비된 환경에서 사용한다. 로컬 `local.properties`는 Android SDK 경로를 지정하는 환경 설정이다.

## 폴드5 속도 측정

### 원본과 수정 버전의 비교 범위

원본은 누적 로그 문자열을 SharedPreferences에서 다시 저장하고, 수정 버전은 비동기 파일 추가 기록과 캐시를 사용한다. 원본의 보조 앱 메뉴는 팝업 표시 단계에서 생성하지만 수정 버전은 목록을 펼칠 때 생성한다. 이 차이는 표시 전에 필요한 작업량을 줄이는 변경이다. 실제 속도 차이는 동일 조건의 원본·수정 버전 측정으로 확인해야 한다.

아래 표는 모두 로컬 수정 버전의 측정이며, 미수정 원본과의 비교 표가 아니다. 특히 124~138ms와 123ms의 비교는 수정 버전 내 추가 최적화 전후를 뜻한다.

### 수정 버전 측정 결과

측정 기기는 Galaxy Z Fold5 SM-F946N이며, 오락 앱 `com.skplanet.ocb.locker`의 외부 링크로 URLCheck를 열었다. 값은 Android `ActivityTaskManager`의 `Displayed` 로그 기준이다. 손가락 클릭부터의 전체 시간이나 모든 URL 분석 완료 시간을 뜻하지 않는다.

| 측정 구간 | 각 표시 시간 | 요약 |
|---|---|---|
| 수정 버전의 추가 최적화 전 첫 테스트 | 235, 135, 119, 128, 115ms | 전체 평균 146.4ms, 2~5회 평균 124.25ms |
| 강제 종료 후 테스트 | 731, 117, 156, 114, 166ms | 최초 실행 731ms, 이후 평균 138.25ms |
| 반복 실행 최적화 후 | 140, 115, 100, 138ms | 평균 123.25ms, 최솟값 100ms, 최댓값 140ms |

강제 종료 후에는 첫 링크 요청에 따라 프로세스가 새로 생성됐음을 확인했다. 최초 실행 731ms는 단일 표본이다. 최적화 후 테스트는 5회를 요청했으나 시작 마커 이후에는 4회의 실행 요청과 표시 로그만 있어 4회만 집계했다. 준비 실행은 제외했다.

최적화 후 평균은 이전 반복 실행 평균 124~138ms와 비슷하거나 소폭 빠른 수준이다. 표본 수와 실행 조건 변동을 고려하면 뚜렷한 성능 향상을 입증한 결과로 보기는 어렵다. 각 측정 구간에서 크래시·ANR·OutOfMemoryError 로그는 발견되지 않았고, 최적화 후 구간에는 프로세스 재시작도 없었다.

원본 로그와 집계 파일은 저장소 외부의 다음 경로에 보관했다.

`C:/Users/enLioN/Documents/ChatGPT/EnLioN/urlcheck-popup-test-20261029`

- `logcat.txt`, `result.txt`: 첫 측정
- `cold-logcat.txt`, `cold-result.txt`: 강제 종료 후 측정
- `warm-optimized-logcat.txt`, `warm-optimized-result.txt`: 반복 실행 최적화 후 측정

## 재측정 방법

1. 같은 버전을 설치하고 애니메이션, 화면 방향, 모듈 설정, 접힘 상태를 동일하게 유지한다.
2. 같은 오락 앱의 같은 링크를 한 번 열고 닫아 준비 실행을 한다.
3. 로그에 시작 마커를 기록한다.
4. 같은 링크로 열기와 뒤로 가기 닫기를 약 3초 간격으로 5회 이상 반복한다. 중간에 강제 종료하지 않는다.
5. 종료 마커를 기록하고 요청·표시 횟수가 맞는지 확인한다.
6. 프로세스 재시작이 있었던 실행은 따로 분류하고, 평균·중앙값·범위를 비교한다.
7. 다른 앱으로 열기 목록, 고정 브라우저, 선택 이력도 정상 동작하는지 확인한다.

완전한 최초 실행 측정은 강제 종료 후 별도 구간으로 수행한다. 반복 실행 측정에 섞지 않는다.

## 후속 과제

- 반복 실행의 모듈 초기화, 앱 후보 조회, 레이아웃 생성 시간을 구간별 측정한다.
- 최적화 전후 동일 조건의 표본을 늘려 개선 여부를 판단한다.
- 네 가지 방향 설정과 접힘·펼침·회전 시 URL 및 화면 상태 유지를 실기기에서 확인한다.
- 지연 생성한 다른 앱 목록을 수동 확인한다.
- 최초 실행 최적화는 반복 실행 검증 이후 진행한다.

기존 개발·번역 지침은 [CONTRIBUTING.md](CONTRIBUTING.md), 개인정보 관련 안내는 [PRIVACY POLICY.md](PRIVACY%20POLICY.md)를 참고한다. 이 문서는 로컬 변경과 검증 기록이며 기존 정책 문서를 대체하지 않는다.
