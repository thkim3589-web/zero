# 15단계 — 전면 디자인 개편 (UI/UX 업그레이드)

"버튼도 디자인도 별로 안 이쁘다, UI/UX를 지금보다 훨씬 업그레이드해달라"는
명시적 요청에 따라 앱 전체의 시각 디자인을 다시 잡았다. 참고로 공유받은
웹앱(PWA)의 남색+주황 배색과 카드형 레이아웃 감성을 기준으로 삼았다.

## 디자인 토큰

- `colors.xml` 전면 교체: `primary`(남색 `#0F2A4A`, 구조/헤더용) +
  `accent`(주황 `#E8871E`, 저장·추가 등 핵심 동작용) 2축 배색. 상태칩용
  (`status_draft`/`status_submitted`/`status_exported`)과 메뉴 아이콘 배지용
  (`badge_teal`/`badge_purple`/`badge_blue`) 팔레트도 추가.
- `themes.xml`에 `shapeAppearanceSmallComponent` 테마 속성을 얹어서 버튼/카드
  모서리를 **앱 전체에서 한 번에** 둥글게(14dp) 만들었다 — 화면마다 스타일을
  일일이 바꿀 필요가 없다. `materialButtonStyle`/`materialButtonOutlinedStyle`/
  `borderlessButtonStyle` 테마 속성도 커스텀 스타일로 교체해서, 이미 있던
  화면들도 스타일 이름을 바꾸지 않고 자동으로 새 디자인을 물려받는다.
- 겪은 실수: `Widget.Jijang.EditText`처럼 점(`.`)이 들어간 커스텀 스타일
  이름에 `parent=`를 명시하지 않으면 aapt가 `Widget.Jijang`을 부모로
  잘못 추론해서 "style not found" 빌드 에러가 난다 — 점으로 구분된 커스텀
  스타일은 항상 `parent`를 명시해야 함을 재확인.

## 화면별 개편

- **현장 목록(`MainActivity`/`item_site_case.xml`)**: 아이콘 배지 + 공사명/
  주소(핀 아이콘)/상태칩 + chevron 구조의 카드 행으로 재작성. 빈 목록일 때
  아이콘+안내문구 형태의 empty state 추가. 툴바에 항상 보이는 `ImageButton`
  기반 메뉴 버튼을 둬서 "점3개가 안 보인다"는 문제를 해결(기존 Toolbar
  내장 overflow 아이콘 방식은 리소스 링킹 에러로 포기).
- **현장 상세(`CaseDetailActivity`)**: 남색 헤더(공사명/주소/상태칩) +
  아이콘 배지가 있는 카드형 메뉴 목록(`item_menu_row.xml`)으로 재작성.
  공유 드로어블(`bg_icon_badge`)에 `.mutate()` 없이 색만 바꾸면 같은 배경을
  쓰는 다른 뷰까지 같이 물드는 `ConstantState` 공유 문제가 있어 미리 방지.
- **체크리스트 입력(`ChecklistEditActivity`/`FormBuilder`)**: 14단계에서 만든
  접이식 섹션을 이번에 카드형으로 다시 다듬었다 — 헤더에 번호 배지(섹션마다
  다른 색) + 제목 + 탭하면 90도 회전하는 화살표 아이콘을 추가하고, 본문을
  `MaterialCardView`로 감쌌다. 2지선다 토글 버튼도 평범한 사각형 배경
  색상에서 둥근 pill 버튼(선택 시 초록/빨강, 미선택 시 옅은 회색 테두리)으로
  바꿔서 지금 상태가 한눈에 들어오게 했다.
- **현장 사진(`PhotoListActivity`/`item_site_photo.xml`)**: 사진 위에
  슬롯 번호 배지와 삭제 버튼을 반투명 오버레이로 올리는 카드형 레이아웃으로
  재작성. 입력칸은 공통 `Widget.Jijang.EditText` 스타일 적용.
- **원가계산 수량(`activity_work_quantity.xml`)**: 노무/장비, 자재 두 섹션을
  각각 카드로 감싸서 시각적으로 분리. 항목 행은 삭제 버튼을 옅은 빨강 원형
  아이콘 버튼으로, 수량 입력은 공통 입력칸 스타일로 교체.
- **구간별 상세(`item_route_segment_row.xml`)**: 지금까지 "선택해야 하는
  항목"(작업구분/코어수/포설방식/접속구분/안전조치)이 평범한 텍스트처럼
  보여서 탭 가능한 컨트롤인지 알아보기 어려웠던 문제를, 입력칸과 같은 박스
  배경 + 화살표 아이콘(`ic_chevron_right`, 오른쪽 정렬)을 붙여서 명확한
  드롭다운처럼 보이게 고쳤다. 라디오 버튼에도 남색 틴트를 입혔다.
- **개황도 편집(`activity_diagram_edit.xml`)**: 하단 버튼 두 줄을 흰색 표면
  바(bar)로 감싸서 캔버스 영역과 시각적으로 분리했고, "이미지를 먼저
  불러오세요" 안내 문구를 아이콘이 있는 카드형 안내 박스로 바꿨다.
- **단가표 업로드(`activity_price_upload.xml`)**: 연도 입력/파일 선택
  섹션을 카드로 감싸고, 업로드 이력 각 행도 배경 박스가 있는 카드 행으로
  바꿔서 이력이 리스트처럼 잘 구분되게 했다.

## 의도적으로 보류한 것

- `DiagramCompositor`/`RouteEditorView`의 선·마커 색상을 13단계에서 발견한
  진짜 개황도 범례(신설=파랑 계열, 기설=노랑 계열, 철거=검정 계열 등 2축
  체계)에 맞추는 작업은 이번 디자인 개편 범위에 포함하지 않았다 — 이건
  시각 디자인이 아니라 데이터 정확성 문제라 별도 단계로 남겨둠.

## 빌드 검증

각 화면을 수정할 때마다 `./gradlew :app:assembleDebug`로 확인했고, 최종
`BUILD SUCCESSFUL`. 실기기(에뮬레이터 포함)가 연결되어 있지 않아 시각적
확인은 APK를 사용자에게 전달해 직접 확인받는 방식으로 진행.
