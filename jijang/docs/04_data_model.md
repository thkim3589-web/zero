# 4단계 — 데이터 모델 설계 (Room 엔티티)

3단계 셀 매핑 + 2단계 단가 스키마를 기준으로 안드로이드 로컬 DB(Room) 엔티티를 정의한다.
실제 Kotlin 코드는 5단계 프로젝트 골격에 그대로 반영한다.

## 1) 현장 건 (Case) 관련

```kotlin
@Entity(tableName = "site_case")
data class SiteCase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseName: String,              // 공사명 (ENG!B3)
    val companyName: String,           // 협력사명/설계자 (B4)
    val reviewer: String?,             // 검토자 (B5)
    val approver: String?,             // 승인자 (B6)
    val businessType: String,          // 사업구분 (D4) RISK 등
    val constructionMethod: String,    // 공사방안 (D5) 절체 등
    val requestSubject: String,        // 요청주체 (D6)
    val extractionType: String,        // 적출유형 (F4)
    val requestDocNo: String?,         // 이설요청 공문번호 (F5)
    val siteAddress: String,           // 현장주소 (F6)
    val effectiveYear: Int,            // 적용 단가 연도 (단가 마스터 조회 키)
    val status: String = "DRAFT",      // DRAFT / SUBMITTED / EXPORTED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "checklist_detail",
    foreignKeys = [ForeignKey(SiteCase::class, ["id"], ["siteCaseId"], onDelete = ForeignKey.CASCADE)]
)
data class ChecklistDetail(
    @PrimaryKey val siteCaseId: Long,
    // 설계 요약
    val relocationMethod: String?,         // 이설 방법 (C11)
    val relocationReason: String?,         // 절체사유 (C12)
    val existingRouteSummary: String?,     // 이설루트 요약 (C13)
    val routeBeforeConfig: String?,        // 이설 전 루트구성 (C14)
    val routeAfterConfig: String?,         // 이설 후 루트구성 (C15)
    // 공사배경 및 목적
    val causeGis: Boolean = false,         // GIS 불일치 관로/전주 존재 여부 등 (B31~B37 다건, 필요 시 세분화)
    val causeNote: String?,
    // 현장실사결과 / 설계이설루트 요약 텍스트 (자유서식이 많아 텍스트로 보관)
    val fieldSurveyNote: String?,
    val designRouteNote: String?,
    val specialNote: String?                // 특이사항 (B63)
)
```

## 2) 기설/신설 시설물 (반복 리스트)

```kotlin
enum class FacilityPhase { EXISTING, NEW }        // 기설 / 신설

@Entity(tableName = "cable_item")
data class CableItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val phase: FacilityPhase,
    val seq: Int,                     // #1, #2 ...
    val lengthM: Double,              // 643M 등
    val networkLevel: String,         // 가입자망/지선망 등
    val cableId: String?,             // =2345= 관리번호
    val totalCore: Int,
    val usedCore: Int,                // 사용률 계산용
    val skbShared: Boolean = false    // SKB 공동투자 여부
)

@Entity(tableName = "enclosure_item")
data class EnclosureItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val phase: FacilityPhase,
    val seq: Int,
    val kepcoNo: String?,             // 한전전산번호/관리번호 (7929DX01 등)
    val jpNo: String?,                // JP 번호 (달서구JP13464 등)
    val connectedCore: Int
)

@Entity(tableName = "pole_info")
data class PoleInfo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val kepcoPoleCount: Int,          // 한전주
    val ownPoleCount: Int,            // 자가주
    val otherCompanyPoleCount: Int    // 타사주
)
```

## 3) 현장 사진

```kotlin
@Entity(tableName = "site_photo")
data class SitePhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val blockIndex: Int,      // 77행 블록=0, 116행 블록=1 ...
    val slotIndex: Int,       // 슬롯1/2/3 (0~2)
    val photoType: String?,   // 사진유형 (현장사진#1 등)
    val description: String?, // 사진설명
    val relatedFacilityNo: String?, // 관련 근거(함체번호 등)
    val localFilePath: String,      // 기기 내 저장 경로
    val capturedAt: Long = System.currentTimeMillis()
)
```

## 4) 배경 이미지 & 경로(선) — 벡터 좌표

> **설계 변경**: 실제 관로/전주/케이블 위치는 Google Maps 등 공개 지도에는
> 없고 사내 GIS 시스템에만 있으므로, 지도 SDK를 쓰지 않고 **담당자가 GIS
> 화면을 캡처한 이미지를 앱에서 불러와 배경으로 사용**하는 것으로 변경.
> 좌표도 위경도가 아니라 **불러온 이미지 기준 상대좌표(0.0~1.0)** 로 저장한다.

```kotlin
enum class RouteType { BEFORE, AFTER }   // 개황도(전) / 개황도(후)

// GIS에서 캡처해서 불러온 배경 이미지 (현장 건 + 전/후 조합당 1장)
@Entity(tableName = "base_map_image")
data class BaseMapImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val routeType: RouteType,
    val localFilePath: String,
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "route_line")
data class RouteLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baseMapImageId: Long,    // 어떤 배경 이미지 위의 선인지
    val lineIndex: Int,          // 한 이미지에 여러 선이 있을 수 있음
    val lineLabel: String?,      // 신설/철거 구분 등
    val colorHex: String = "#FF0000"
)

@Entity(
    tableName = "route_point",
    foreignKeys = [ForeignKey(RouteLine::class, ["id"], ["routeLineId"], onDelete = ForeignKey.CASCADE)]
)
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeLineId: Long,
    val pointOrder: Int,       // 폴리라인 내 순서 (편집 시 재정렬)
    val relativeX: Double,     // 0.0 ~ 1.0 (이미지 좌상단 기준)
    val relativeY: Double
)
```
→ 선은 이미지 자체에 그려 넣는 게 아니라 **좌표 리스트**로 저장되므로, 언제든
점 추가(리스트 삽입)/이동(좌표 갱신)/삭제(row 삭제)만으로 재편집 가능하다.
상대좌표이므로 화면 크기·확대 배율이 달라도 그 배경 이미지에 대해서는 항상
정확한 위치를 가리킨다. 내보낼 때는 저장된 배경 이미지 위에 이 점들을 이어
선을 그린 합성 비트맵을 생성한다 (지도 API 불필요, 캔버스에 직접 그리기).

## 5) 원가계산 — 수량 입력 & 단가 마스터

```kotlin
@Entity(tableName = "work_quantity")
data class WorkQuantity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val masterType: String,     // "LABOR" | "MATERIAL"
    val code: String,           // process_code 또는 material_code
    val quantity: Double,
    val category: String? = null   // 투자/수선 구분 등 필요 시
)

// 2단계 스키마 그대로 Room 엔티티화
@Entity(tableName = "labor_price_master", primaryKeys = ["processCode", "effectiveYear"])
data class LaborPriceMaster(
    val processCode: String,
    val category: String?,
    val workType: String?,
    val itemName: String,
    val spec: String?,
    val unit: String,
    val materialUnitPrice: Int = 0,
    val laborUnitPrice: Int = 0,
    val equipmentUnitPrice: Int = 0,
    val effectiveYear: Int,
    val sourceFile: String?,
    val uploadedAt: Long
)

@Entity(tableName = "material_price_master", primaryKeys = ["materialCode", "effectiveYear"])
data class MaterialPriceMaster(
    val materialCode: String,
    val category: String?,
    val materialName: String,
    val spec: String?,
    val specDetail: String?,
    val unit: String,
    val materialUnitPrice: Int = 0,
    val effectiveYear: Int,
    val sourceFile: String?,
    val uploadedAt: Long
)

@Entity(tableName = "cost_rate_master", primaryKeys = ["rateKey", "effectiveYear"])
data class CostRateMaster(
    val rateKey: String,        // INDIRECT_LABOR_RATE 등
    val rateNameKr: String,
    val rateValue: Double,
    val effectiveYear: Int,
    val sourceFile: String?,
    val uploadedAt: Long
)

@Entity(tableName = "price_upload_history")
data class PriceUploadHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val effectiveYear: Int,
    val laborRowCount: Int,
    val materialRowCount: Int,
    val uploadedAt: Long,
    val uploadedBy: String?
)
```

## ER 관계 요약
```
SiteCase 1--1 ChecklistDetail
SiteCase 1--N CableItem / EnclosureItem / PoleInfo
SiteCase 1--N SitePhoto
SiteCase 1--N BaseMapImage 1--N RouteLine 1--N RoutePoint
SiteCase 1--N WorkQuantity --(code, effectiveYear)--> LaborPriceMaster / MaterialPriceMaster
```

## 다음 단계
5단계에서 위 엔티티들을 실제 Android 프로젝트(`지장이설관리/android`)의
Room Database 클래스로 옮기고, 최소 골격(빈 화면 + DB 초기화)까지 구성한다.
