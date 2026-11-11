package com.thkim.jijang.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.thkim.jijang.data.db.dao.ChecklistDao
import com.thkim.jijang.data.db.dao.DiagramDao
import com.thkim.jijang.data.db.dao.PriceMasterDao
import com.thkim.jijang.data.db.dao.SiteCaseDao
import com.thkim.jijang.data.db.dao.RouteSegmentDao
import com.thkim.jijang.data.db.dao.SitePhotoDao
import com.thkim.jijang.data.db.dao.WorkQuantityDao
import com.thkim.jijang.data.model.BaseMapImage
import com.thkim.jijang.data.model.CableItem
import com.thkim.jijang.data.model.ChecklistDetail
import com.thkim.jijang.data.model.CostRateMaster
import com.thkim.jijang.data.model.EnclosureItem
import com.thkim.jijang.data.model.LaborPriceMaster
import com.thkim.jijang.data.model.MaterialPriceMaster
import com.thkim.jijang.data.model.PoleInfo
import com.thkim.jijang.data.model.PriceUploadHistory
import com.thkim.jijang.data.model.RouteLine
import com.thkim.jijang.data.model.RoutePoint
import com.thkim.jijang.data.model.RouteSegment
import com.thkim.jijang.data.model.SiteCase
import com.thkim.jijang.data.model.SitePhoto
import com.thkim.jijang.data.model.WorkQuantity

@Database(
    entities = [
        SiteCase::class,
        ChecklistDetail::class,
        CableItem::class,
        EnclosureItem::class,
        PoleInfo::class,
        SitePhoto::class,
        BaseMapImage::class,
        RouteLine::class,
        RoutePoint::class,
        WorkQuantity::class,
        LaborPriceMaster::class,
        MaterialPriceMaster::class,
        CostRateMaster::class,
        PriceUploadHistory::class,
        RouteSegment::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun siteCaseDao(): SiteCaseDao
    abstract fun priceMasterDao(): PriceMasterDao
    abstract fun diagramDao(): DiagramDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun sitePhotoDao(): SitePhotoDao
    abstract fun workQuantityDao(): WorkQuantityDao
    abstract fun routeSegmentDao(): RouteSegmentDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * 16단계 — 이전까지는 스키마 버전을 올릴 때마다 `fallbackToDestructiveMigration()`이
         * DB 전체(단가표 포함)를 통째로 지워버렸다. 앱을 계속 고쳐나가는 동안 사용자가
         * "단가표는 실제로 바뀔 때만 다시 올리고, 안 바뀌면 계속 쓰고 싶다"고 요청한 것을
         * 계기로, 최근 스키마 변경 구간(4→5→6→7)은 실제 마이그레이션으로 바꿔서 단가표를
         * 포함한 기존 데이터가 앱 업데이트로 사라지지 않게 했다. 4→5는 ChecklistDetail을
         * ENG시트 전체 항목으로 갈아엎은 변경이라 체크리스트 테이블만 새로 만들고(그 안의
         * 체크리스트 입력 값만 초기화됨), 단가표/현장 목록/구간별 상세 등 다른 테이블은
         * 그대로 보존된다. 5→6, 6→7은 컬럼 추가라 데이터 손실이 전혀 없다.
         * 그 이전 버전(1~3)에서 곧장 올라오는 경우처럼 아직 경로가 없는 예외적인 상황을
         * 대비해 fallbackToDestructiveMigration()은 안전망으로 남겨둔다.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `checklist_detail`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklist_detail` (" +
                        "`siteCaseId` INTEGER NOT NULL, `linkedEngExists` TEXT, `relocationMethod` TEXT, " +
                        "`relocationReason` TEXT, `existingRouteSummary` TEXT, `routeBeforeConfig` TEXT, " +
                        "`routeAfterConfig` TEXT, `coreCountDay` TEXT, `coreCountNight` TEXT, " +
                        "`requestSubjectType` TEXT, `rmRegistrationNo` TEXT, `parallelConstruction` TEXT, " +
                        "`parallelConstructionDetail` TEXT, `causerReportAttached` TEXT, `causerReportReason` TEXT, " +
                        "`gisMismatchDuctPole` TEXT, `gisMismatchDuctPoleDetail` TEXT, `gisMismatchCableEnclosure` TEXT, " +
                        "`gisMismatchCableEnclosureDetail` TEXT, `gisOtherIssue` TEXT, `gisOtherIssueDetail` TEXT, " +
                        "`ownDuctExists` TEXT, `ownDuctExistsDetail` TEXT, `otherCompanyDuctExists` TEXT, " +
                        "`otherCompanyDuctExistsDetail` TEXT, `ownPolePermitPossible` TEXT, `ownPolePermitPossibleDetail` TEXT, " +
                        "`bombOrMultiEnclosure` TEXT, `bombOrMultiEnclosureDetail` TEXT, `useExistingRoute` INTEGER NOT NULL, " +
                        "`useExistingRouteDetail` TEXT, `parallelNewDuctPole` INTEGER NOT NULL, `soleNewDuctPole` INTEGER NOT NULL, " +
                        "`kepcoRelocation` INTEGER NOT NULL, `capacityExpansion` TEXT, `capacityExpansionDetail` TEXT, " +
                        "`multiplexTarget` TEXT, `multiplexTargetDetail` TEXT, `tbJointInvestment` TEXT, `tbJointInvestmentDetail` TEXT, " +
                        "`sixLaneCrossing` TEXT, `sixLaneCrossingDetail` TEXT, `arbitraryCrossingIssue` TEXT, " +
                        "`arbitraryCrossingIssueDetail` TEXT, `distributionContactIssue` TEXT, `distributionContactIssueDetail` TEXT, " +
                        "`coreRingIssue` TEXT, `coreRingIssueDetail` TEXT, `recycledCableUsed` TEXT, `recycledCableUsedDetail` TEXT, " +
                        "`coreConnectionAppropriate` TEXT, `coreConnectionAppropriateDetail` TEXT, `lineDistributionDesignApplied` TEXT, " +
                        "`lineDistributionDesignAppliedDetail` TEXT, `regionalOtherCriteria` TEXT, `regionalOtherCriteriaDetail` TEXT, " +
                        "`specialNote` TEXT, PRIMARY KEY(`siteCaseId`), " +
                        "FOREIGN KEY(`siteCaseId`) REFERENCES `site_case`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `base_map_image` ADD COLUMN `legendImagePath` TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `route_line` ADD COLUMN `dashed` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `route_point` ADD COLUMN `status` TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `route_line` ADD COLUMN `styleId` TEXT")
                db.execSQL("ALTER TABLE `route_point` ADD COLUMN `symbolId` TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `base_map_image` ADD COLUMN `metersPerWidth` REAL")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jijang.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
