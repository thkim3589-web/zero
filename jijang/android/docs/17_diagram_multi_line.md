# 17단계 — 개황도 선 개수 제한 해제 (신설/철거 2개 고정 → N개)

"선을 5개 정도 그려야 할 때는 어떡해?"라는 질문으로 드러난 한계: 지금까지 개황도
편집기는 `FacilityPhase`(EXISTING/철거, NEW/신설) 딱 2가지에 맞춰 선을 정확히
2개까지만 지원했다. 실제로는 가공/지중/FTTH/SKB/T·B공동/임차케이블 등 케이블
종류별로 훨씬 많은 선을 따로 그려야 할 수 있다.

## 데이터 모델

`RouteLine`은 원래부터 `lineIndex`로 개수 제한 없이 여러 선을 저장할 수 있는
구조였다 — 제한은 순전히 `DiagramViewModel`/`DiagramEditActivity`가
`FacilityPhase` 2값에 맞춰 하드코딩되어 있었을 뿐이라, DB 마이그레이션 없이
로직만 일반화하면 됐다.

- `DiagramViewModel.getOrCreateLine(phase)` → `getLines()`/`addLine(label,
  colorHex)`/`deleteLine()`/`renameLine()`로 교체.
- 기존에 신설/철거로 만들어져 있던 선도 그냥 "이름이 신설/철거인 보통 선"으로
  취급되어 그대로 호환된다 — 처음 이미지를 불러올 때 선이 하나도 없으면
  (완전히 새 이미지) 예전처럼 철거(빨강)/신설(파랑) 2개로 시작해주고, 그 뒤로는
  자유롭게 추가/삭제한다.

## 화면 (`DiagramEditActivity`)

기존 "철거선 편집"/"신설선 편집" 2버튼 토글을, 위쪽에 가로 스크롤되는 **선 칩
목록**으로 바꿨다:
- 각 칩은 그 선의 이름 + 색을 보여준다. 탭하면 그 선을 편집 대상으로 전환.
- 칩을 길게 누르면 "이름 변경"/"삭제" 메뉴 (마지막 남은 선 하나는 삭제 불가).
- 맨 끝의 "+ 선 추가" 칩으로 새 선을 몇 개든 만들 수 있다. 이름을 입력받고,
  색은 팔레트에서 아직 안 쓴 색을 순서대로 자동 배정한다 (겹치지 않게).

`RouteEditorView`도 "참고용 선 1개"만 그리던 것을 `setReferenceLines(List<
ReferenceLine>)`로 바꿔서, 지금 편집 중인 선 외 **나머지 선 전부**를 각자의
색/점선 스타일 그대로 반투명하게 같이 보여준다 — 5개든 10개든 한 화면에서
서로 겹치지 않는지 확인하면서 그릴 수 있다.

`DiagramCompositor`/`XlsxExporter`는 애초에 `List<LineRender>`로 일반적으로
작성되어 있었어서 이번 변경에 손댈 필요가 없었다 — 몇 개 선을 그리든 그대로
합성/내보내기된다.

## 빌드 검증

`./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`. DB 스키마 변경은 없어서
(기존 `RouteLine`/`RoutePoint` 테이블 구조 그대로) 별도 마이그레이션도 필요
없었다.
