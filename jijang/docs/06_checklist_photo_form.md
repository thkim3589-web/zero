# 6단계 — 체크리스트/사진 입력 폼

5단계 골격(현장 목록 → 현장 상세 → 개황도 편집) 위에, `ENG시트(Checklist)`와
`공문 및 사진` 시트에 대응하는 입력 화면 두 개를 추가했다.

## 1) 체크리스트 입력 (`ChecklistEditActivity`)

현장 상세 화면에 "체크리스트 입력" 버튼을 추가해 진입한다. 한 화면(스크롤 폼)에서:

- 설계 요약 (이설 방법/절체사유/이설루트 요약/전·후 루트구성) — `ChecklistDetail`
- 기설/신설 케이블 목록 — 행 추가/삭제가 가능한 반복 입력 (`CableItem`,
  `FacilityPhase.EXISTING`/`NEW`로 구분)
- 기설/신설 함체 목록 — 동일한 방식 (`EnclosureItem`)
- 전주 정보 (한전주/자가주/타사주 수량) — `PoleInfo`
- 현장실사결과/설계 이설루트 메모, 특이사항

행 추가/삭제는 RecyclerView 대신 컨테이너 `LinearLayout`에 `item_cable_row.xml`
/ `item_enclosure_row.xml`을 직접 inflate/remove하는 방식으로 구현했다 —
한 현장당 행 수가 적고(수 개~수십 개) 뷰 재활용이 필요 없어 더 단순하다.
"저장"을 누르면 각 목록을 통째로 지우고 현재 화면 상태로 다시 insert한다
(`ChecklistDao.replaceCableItems`/`replaceEnclosureItems`).

## 2) 현장 사진 관리 (`PhotoListActivity`)

현장 상세 화면에 "현장 사진 관리" 버튼을 추가해 진입한다.

- 우측 하단 버튼 → "카메라로 촬영" / "갤러리에서 선택" 중 선택
- 카메라 촬영은 `FileProvider`로 앱 내부 저장소에 바로 저장하고
  (`ImageUtil.createCaptureFile`), 갤러리 선택은 기존 개황도 화면과 동일하게
  내부 저장소로 복사한다 (`ImageUtil.copyToInternalStorage`)
- 사진마다 유형/설명/관련 근거(함체번호 등) 텍스트를 입력할 수 있고,
  입력란에서 포커스가 벗어날 때 DB에 반영된다 (`SitePhotoAdapter`)
- 블록/슬롯 번호(`blockIndex`/`slotIndex`, 3장 1세트)는 추가 순서대로 자동 배정
  — 3단계 셀 매핑 문서의 "77행 블록, 116행 블록 ..." 반복 패턴과 대응

## 추가된 인프라

- `ChecklistDao`, `SitePhotoDao` (기존 5개 엔티티는 그대로, DAO만 신규 추가 —
  DB 스키마 버전 변경 없음)
- `FileProvider` 등록 (`res/xml/file_paths.xml`, 매니페스트) — 카메라 촬영본을
  앱 내부 저장소에 바로 쓰기 위해 필요. `CAMERA` 권한은 4단계에서 이미 추가돼 있었음

## 빌드 검증
```
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew :app:assembleDebug
BUILD SUCCESSFUL
```
(이 프로젝트는 로컬에 `JAVA_HOME`이 설정돼 있지 않아, Android Studio 내장 JBR을
임시로 지정해서 빌드했다. 커맨드라인에서 반복해서 빌드하려면 `JAVA_HOME`을
영구적으로 설정해두는 것을 권장.)

## 아직 안 한 것 (다음 단계)
- 단가표 업로드 파서 (7단계)
- 원가계산 수량 입력 화면 (8단계)
- 엑셀 생성 엔진 (9단계)
- 다중 선(신설/철거 구분) 지원
- 사진 슬롯 재배치(드래그 순서 변경), 블록당 3장 초과 시 UX 검토
