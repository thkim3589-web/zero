# 14단계 — ENG시트 전체 항목(약 50개) 반영 + 체크리스트 화면 전면 재작성

참고용으로 공유받은 웹앱(PWA) 구현을 검토한 뒤 "적용할 만한 게 있는지" 확인하는
과정에서, 지금까지 `ChecklistDetail`이 ENG시트의 극히 일부(이설방법/절체사유/
루트요약 등 10여 개)만 담고 있었다는 게 드러났다. 실제 ENG시트에는 공사배경,
현장실사결과 7종, 기설/신설 케이블·함체 목록, 전주 수량, RM여부 4종, 지역별
추가기준 4종 등 약 50개 필드가 있다. 처음엔 사용자가 "안 해도 된다"(`ㄴ`)고
했으나, 곧이어 "좋은 것들 다 반영해달라"고 요청을 뒤집으면서 전체 확장을
진행했다.

## 데이터 모델

`ChecklistDetail`을 전면 재작성. 2지선다 항목(예: "선번대체 여부")은 boolean이
아니라 **실제 셀에 쓸 한글 라벨 문자열**(`String?`)로 저장한다 — 엑셀 내보내기
시점에 다시 라벨로 변환할 필요가 없게 하기 위한 결정. 체크박스류만 `Boolean`
유지. 상세내역이 딸린 항목은 `xxx` + `xxxDetail` 페어로 이름을 통일했다
(`EngSheetComposer`의 기존 `pair()` 패턴과 맞춤).

DB 버전 4 → 5, `fallbackToDestructiveMigration()` 유지 (운영 데이터가 없는
개발 단계라 마이그레이션 스크립트 대신 파괴적 갱신을 계속 사용).

## `FormBuilder` — 반복 필드 유형을 코드로 생성

50개 필드를 전부 XML로 손으로 쓰면 실수하기 쉽고 유지보수가 어려워서, 대신
`ui/checklist/FormBuilder.kt`를 새로 만들어 필드 유형별 헬퍼 함수로 처리한다:

- `textField` — 라벨 + 입력칸, `() -> String?` 반환
- `toggleField` / `toggleWithDetail` — 2지선다 + (옵션) 상세 텍스트 한 줄
- `checkboxField` — 체크박스
- `selectField` — 탭하면 다이얼로그가 뜨는 단일 선택
- `radioGroupField` — 가로로 나열된 선택 칩

각 함수는 **현재 값을 읽는 클로저**를 돌려주고, `ChecklistEditActivity`는 이걸
`stringFields: MutableMap<String, () -> String?>` 등에 모아뒀다가 저장 시점에
한 번에 호출해서 값을 수집한다. XML에는 값이 채워질 빈 컨테이너
(`containerFieldSurvey`, `containerRmStatus` 등)만 두고, `populate()`에서
`FormBuilder`로 실제 뷰를 채워 넣는 구조.

## 아코디언(접이식 섹션)

필드 50개를 한 화면에 flat하게 나열하면 스크롤이 끝없이 길어져서 실사용이
어려울 거라 판단, `makeSectionsCollapsible()`을 추가했다. XML에서
`android:tag="section_header"`가 붙은 헤더를 찾아 그 다음 헤더 전까지의
형제 뷰들을 하나의 접이식 그룹으로 묶는다. 첫 섹션(기본정보)만 기본으로
펼쳐두고 나머지는 접어서 시작한다.

## `EngSheetComposer` 확장

새로 추가된 필드에 대응하는 셀 쓰기를 모두 추가했다 (B7, C18/D18,
C21~C28/D23/D27/D28, C31~D37 페어, C46~D49, C51~D53, C55~D62 페어). 토글+상세
페어를 위한 `pair(cells, toggleCell, detailCell, toggleValue, detailValue)`
헬퍼를 새로 뒀다.

## 겪은 문제 — AAPT2 "Cannot read field elmName because root is null"

아코디언 헤더 스타일을 일괄 적용하려고 PowerShell 정규식으로
`style="@style/SectionHeader"` → `style="@style/SectionHeaderCollapsible" ...`
치환을 돌렸는데, 이때 PowerShell이 파일을 **UTF-8 BOM 포함**으로 다시 썼다.
이 BOM이 AAPT2의 리소스 병합기를 internal NPE로 죽여서(`elmName`/`root` null
참조 — 일반적인 "리소스 못 찾음" 에러가 아니라 파서 자체가 죽는 형태), 정상
XML인데도 빌드가 실패했다. `xxd`로 파일 첫 바이트를 확인해 `ef bb bf`를
발견하고 제거해서 해결. **교훈: PowerShell로 UTF-8 파일(특히 한글 리소스)을
정규식 치환할 때는 항상 BOM 여부를 확인할 것.**

## 빌드 검증
```
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew :app:assembleDebug
BUILD SUCCESSFUL
```
