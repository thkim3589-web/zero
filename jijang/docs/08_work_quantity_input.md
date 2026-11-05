# 8단계 — 원가계산 수량 입력

`내역서_일반`/`자재내역서_일반`에 채울 수량을 현장 상세 화면에서 입력하는 화면.
7단계에서 올려둔 단가 마스터 중 이 현장의 `effectiveYear`에 해당하는 항목을
검색해서 골라 담고, 수량만 입력하면 된다 (품명/규격/단가는 코드로 조회되므로
다시 타이핑하지 않는다).

## 구성

- **`WorkQuantityEditActivity`** — 현장 상세 화면의 "원가계산 수량 입력" 버튼으로
  진입. 노무/장비, 자재 두 섹션에 각각 행을 추가/삭제할 수 있다 (체크리스트 화면과
  같은 방식: RecyclerView 대신 컨테이너에 직접 inflate/remove).
- **`PriceItemPickerDialog`** — "+ 항목 추가"를 누르면 뜨는 검색 다이얼로그.
  품명/코드로 검색하면 해당 연도(`effectiveYear`) 단가 마스터에서 최대 50건까지
  결과를 보여주고, 탭하면 수량 0으로 행이 추가된다.
- **`WorkQuantityDao`**, `PriceMasterDao`의 `searchLaborPrices`/`searchMaterialPrices`
  (신규 추가) — 이름 검색은 `LIKE '%검색어%'`로 처리했다. 데이터가 많아지면
  느려질 수 있어 결과를 50건으로 제한해뒀다.
- 저장 시 `WorkQuantityDao.replaceAll`로 해당 현장의 LABOR/MATERIAL 행을
  통째로 교체한다 (체크리스트의 케이블/함체 목록과 동일한 패턴).

## 참고: 단가 마스터 컬럼 매핑, 실제 2026년 파일로 검증 완료

7단계 문서에 적어둔 대로, 이번에 사용자가 업로드해준 실제 2026년 JP13464 정산
파일로 `내역서_일반`/`자재내역서_일반`의 열 위치를 직접 대조해봤고 문서(02단계)
예시와 정확히 일치함을 확인했다. 자세한 내용은
[07_price_upload_parser.md](07_price_upload_parser.md)의 "실제 2026년 파일로
컬럼 매핑 검증 완료" 절 참고.

## 빌드 검증
```
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew :app:assembleDebug
BUILD SUCCESSFUL
```

## 아직 안 한 것 (다음 단계)
- 9단계: 엑셀 생성 엔진 — 이제 체크리스트/사진/경로/수량/단가까지 입력 데이터가
  모두 모였으므로, 이 데이터 + `cost_rate_master` 요율을 조합해서 원본과 동일한
  형식의 `내역서_일반`/`자재내역서_일반`/`원가계산서(일반)`/`ENG시트`/`공문 및
  사진`/`개황도` 결과물을 xlsx로 생성
- 단가 검색 다이얼로그 성능 — 마스터 데이터가 많아지면 LIKE 검색 대신 인덱스나
  전체 목록 캐싱 검토
