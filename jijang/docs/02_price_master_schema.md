# 2단계 — 단가표 / 요율표 마스터 데이터 구조 설계

`내역서_일반`, `자재내역서_일반`, `원가계산서(일반)` 시트를 실제로 파싱해서 나온
컬럼 구조를 기준으로 설계함 (원본: `불량함체_월배간102(달서구JP13464)` 샘플 파일).

## 1) 노무/장비 단가 마스터 — `labor_price_master`
`내역서_일반` 시트 대응 (엑셀 열: B,C,D,E,F,G,H,O,P,Q)

| 필드명 | 타입 | 엑셀 열 | 설명 | 예시 |
|---|---|---|---|---|
| id | INTEGER PK | - | 내부 ID | - |
| process_code | TEXT | E (25년 공정번호) | 단가 조회 키 | 11773 |
| category | TEXT | C (구분) | 추가할증 등 | 추가할증 |
| work_type | TEXT | D (공종) | 대분류 | 선로-1.케이블 포설 |
| item_name | TEXT | F (공종명칭) | 세부 작업명 | 14-5-1-1. 광케이블 포설(소규모할증 500m이하) |
| spec | TEXT | G (규격) | 시공 조건 | 지중관로 케이블 포설 |
| unit | TEXT | H (단위) | m, 개소 등 | m |
| material_unit_price | INTEGER | O (직접재료비 단가) | 원/단위 | 0 |
| labor_unit_price | INTEGER | P (직접노무비 단가) | 원/단위 | 4395 |
| equipment_unit_price | INTEGER | Q (기계경비 단가) | 원/단위 | 0 |
| effective_year | INTEGER | - | 적용 연도 (버전 관리 키) | 2025 |
| source_file | TEXT | - | 업로드된 원본 파일명 | 경북_지장_2025년 선로내역서_rev1_250425.xlsx |
| uploaded_at | TEXT | - | 업로드 일시 | - |

```sql
CREATE TABLE labor_price_master (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  process_code TEXT NOT NULL,
  category TEXT,
  work_type TEXT,
  item_name TEXT NOT NULL,
  spec TEXT,
  unit TEXT NOT NULL,
  material_unit_price INTEGER NOT NULL DEFAULT 0,
  labor_unit_price INTEGER NOT NULL DEFAULT 0,
  equipment_unit_price INTEGER NOT NULL DEFAULT 0,
  effective_year INTEGER NOT NULL,
  source_file TEXT,
  uploaded_at TEXT NOT NULL,
  UNIQUE(process_code, effective_year)
);
```

## 2) 자재 단가 마스터 — `material_price_master`
`자재내역서_일반` 시트 대응 (엑셀 열: B,C,D,E,F,G,H,L)

| 필드명 | 타입 | 엑셀 열 | 설명 | 예시 |
|---|---|---|---|---|
| id | INTEGER PK | - | 내부 ID | - |
| material_code | TEXT | C (25년 공정NO) | 단가 조회 키 | 12754 |
| category | TEXT | D (분류) | 케이블 등 | 케이블 |
| material_name | TEXT | E (자재명) | 광케이블 등 | 광케이블 |
| spec | TEXT | F (규격) | 관로/난연 등 | 관로 |
| spec_detail | TEXT | G (규격상세) | 코어수 등 | 12 |
| unit | TEXT | H (단위) | m 등 | m |
| material_unit_price | INTEGER | L (25년 자재단가) | 원/단위 | 679 |
| effective_year | INTEGER | - | 적용 연도 | 2025 |
| source_file | TEXT | - | 업로드 원본 파일명 | - |
| uploaded_at | TEXT | - | - | - |

```sql
CREATE TABLE material_price_master (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  material_code TEXT NOT NULL,
  category TEXT,
  material_name TEXT NOT NULL,
  spec TEXT,
  spec_detail TEXT,
  unit TEXT NOT NULL,
  material_unit_price INTEGER NOT NULL DEFAULT 0,
  effective_year INTEGER NOT NULL,
  source_file TEXT,
  uploaded_at TEXT NOT NULL,
  UNIQUE(material_code, effective_year)
);
```

## 3) 원가계산 요율 마스터 — `cost_rate_master`
`원가계산서(일반)` 시트의 실제 행 라벨(B/C/D열)까지 대조 확인한 정확한 값
(표준 원가계산서 양식 — 순공사원가 → 일반관리비 → 이윤 순서)

| rate_key | rate_name_kr | 산정 기준 | 실측값(2025) |
|---|---|---|---|
| INDIRECT_LABOR_RATE | 간접노무비 | 직접노무비의 | 10% |
| INDUSTRIAL_ACCIDENT_INSURANCE_RATE | 산재보험료 | 노무비의 | 3.56% |
| EMPLOYMENT_INSURANCE_RATE | 고용보험료 | 노무비의 | 1.01% |
| NATIONAL_PENSION_RATE | 국민연금보험료 | 직접노무비의 | 4.50% |
| HEALTH_INSURANCE_RATE | 국민건강보험료 | 직접노무비의 | 3.545% |
| SAFETY_MANAGEMENT_RATE | 안전관리비 | 재료비+직접노무비의 | 1.64% (E열) / 1.27% (N열)* |
| LONGTERM_CARE_INSURANCE_RATE | 노인장기요양보험 | 건강보험료의 | 12.95% |
| GENERAL_MANAGEMENT_RATE | 일반관리비 | 재료비+노무비+경비의 | 3% 이내 |
| PROFIT_RATE | 이윤 | 노무비+경비+일반관리비의 | 13.5% 이내 |
| BID_SUCCESS_RATE | 낙찰가율 | 공사비의 | 74.9% |

\* E열/N열이 다른 값을 갖는 항목(안전관리비)이 하나 있음 — 시트 내 두 계산 계열
(예: 투자/수선, 또는 원 계산 vs 검증용 재계산)의 차이로 추정되며, **3단계에서
두 열의 정확한 용도를 확인해 스키마에 반영**한다.

퇴직공제부금비(3-공사비 100억 이상 시), 도서/도심지/KTX/휴일 할증(조건부 적용)은
상시 적용되는 고정 요율이 아니라 **현장 조건에 따라 켜고 끄는 항목**이므로
`cost_rate_master`가 아니라 현장 입력값(조건 플래그)으로 별도 관리한다.

```sql
CREATE TABLE cost_rate_master (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  rate_key TEXT NOT NULL,
  rate_name_kr TEXT NOT NULL,
  rate_value REAL NOT NULL,
  effective_year INTEGER NOT NULL,
  source_file TEXT,
  uploaded_at TEXT NOT NULL,
  UNIQUE(rate_key, effective_year)
);
```

## 4) 업로드 이력 — `price_upload_history`
관리자가 단가 파일을 업로드할 때마다 기록 (감사 추적용)

```sql
CREATE TABLE price_upload_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  file_name TEXT NOT NULL,
  effective_year INTEGER NOT NULL,
  labor_row_count INTEGER NOT NULL DEFAULT 0,
  material_row_count INTEGER NOT NULL DEFAULT 0,
  uploaded_at TEXT NOT NULL,
  uploaded_by TEXT
);
```

## 버전 관리 원칙
- 모든 단가/요율은 `effective_year`로 구분하여 **덮어쓰지 않고 누적 저장**한다.
- 새 현장 건을 생성할 때는 해당 연도의 최신 단가를 자동 조회하고,
  이미 생성된 과거 건을 다시 열람할 때는 그 건이 생성된 시점의 연도 단가를 그대로 사용한다.
  (템플릿에도 `24년 단가` vs `25년 단가`를 나란히 비교하는 열이 이미 존재 — 동일한 관행)

## 다음 단계에서 필요한 것
최신 "선로내역서" 단가 파일(예: `경북_지장_2025년 선로내역서_rev1_250425.xlsx`)을
받으면, 위 스키마의 컬럼 매핑이 실제 최신 양식과 일치하는지 검증하고
업로드 파서(엑셀 → DB) 로직의 입력 스펙을 확정한다.
