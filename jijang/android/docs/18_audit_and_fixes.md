# 18단계 — 전체 코드 감사 및 발견된 버그 수정

"전체 오류 및 앱 검증 후 개선 필요 사항 확인" 요청에 따라 서브에이전트로 전체
코드베이스를 감사했다. DB 마이그레이션(4→5~7→8), 개황도 다중 선 리팩터링,
체크리스트 아코디언, 팝업 메뉴 교체 등 이번 세션에 크게 바뀐 영역을 중심으로
실제 파일을 전부 읽게 했다. 빌드는 이미 통과 상태였고, 감사에서 확정된 버그
2건과 사소한 문제 1건을 이번 단계에서 고쳤다.

## 고친 것

### 1. (심각) 개황도 이미지를 다시 고르면 그동안 그린 모든 선/점이 삭제됨

`DiagramViewModel.setBaseMapImage()`가 이미지를 다시 불러올 때마다 새
`BaseMapImage`를 `@Insert(onConflict = REPLACE)`로 밀어넣고 있었다.
`base_map_image`는 `(siteCaseId, routeType)`에 UNIQUE 인덱스가 걸려 있고,
`route_line`은 `baseMapImageId`에 `ON DELETE CASCADE` 외래키가 걸려 있다 —
즉 REPLACE가 기존 행을 지우고 새로 넣는 과정에서 그 위에 그려둔 모든 선과
점이 CASCADE로 같이 삭제됐다. GIS 캡처를 잘못 떠서 다시 불러오는 것처럼
지극히 평범한 동작 중에 아무 경고 없이 작업 내용이 사라지는 심각한 버그였다.

고침: 기존 행이 있으면 `UPDATE`로 경로만 바꾸고(`DiagramDao.
updateBaseMapImagePath`), id를 그대로 유지 — 완전히 새 이미지일 때만 insert.

### 2. (중간) 사진을 지웠다 다시 추가하면 다른 사진과 엑셀 칸이 겹칠 수 있음

`PhotoViewModel.addPhoto()`가 슬롯 번호를 `count/3, count%3`(현재 개수 기준)로
매기고 있었다. 가운데 사진을 지우면 개수는 줄지만 뒤쪽 사진들이 차지한 칸은
그대로라서, 새로 추가한 사진이 이미 다른 사진이 쓰고 있는 칸과 겹칠 수 있었다
— 엑셀 내보내기 시 두 사진이 같은 칸에 합성되어 한쪽이 안 보이게 된다.

고침: 실제 비어있는 `(blockIndex, slotIndex)`를 찾아서 채우도록 변경
(`SitePhotoDao.getPhotos()` 추가).

### 3. (사소) 선을 삭제해도 화면에 참고선이 그대로 남아있음

`confirmDeleteLine()`에서 삭제된 선이 활성 선이 아니면 `applyActiveLineToView()`를
안 불러서, 참고용으로 반투명하게 보이던 그 선이 화면에서 안 사라지고 있었다
(DB 삭제 자체는 정상). 항상 다시 그리도록 수정.

## 확인했지만 문제 없었던 것 (감사에서 별도로 검증)

- Room 마이그레이션 4→5~7→8: `app/schemas/.../8.json`과 한 줄씩 대조 — 정확함.
- `RouteEditorView`의 핀치줌 좌표 변환: 탭 추가/즉시배치 경로 모두 일관됨.
- Converters/DAO/ViewModel 시그니처 전수 대조 — 누락 없음.
- MainActivity의 커스텀 PopupWindow — 이전 PopupMenu 잔재 없이 깔끔.

## 개선 검토가 필요한 것 (이번엔 손대지 않음)

- 체크리스트 2지선다 필드가 `null`(미선택) 대신 항상 "거짓" 라벨로 시작 —
  `ChecklistDetail`의 문서 주석("null=아직 선택 안 함")과 실제 UI 동작이
  어긋난다. 의도적인 설계(기본값 없음 취급)인지 확인 필요.
- 사진 삭제 시 DB 행만 지우고 실제 이미지 파일은 안 지움 — 내부 저장소에
  파일이 계속 쌓인다 (크래시는 아니지만 저장공간 누수).

## 빌드 검증
`./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`.
