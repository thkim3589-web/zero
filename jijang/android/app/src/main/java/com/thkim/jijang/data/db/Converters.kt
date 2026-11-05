package com.thkim.jijang.data.db

import androidx.room.TypeConverter
import com.thkim.jijang.data.model.FacilityPhase
import com.thkim.jijang.data.model.LayingMethod
import com.thkim.jijang.data.model.MarkerStatus
import com.thkim.jijang.data.model.MarkerType
import com.thkim.jijang.data.model.RouteType
import com.thkim.jijang.data.model.SafetyMeasure
import com.thkim.jijang.data.model.SpliceType
import com.thkim.jijang.data.model.StockType
import com.thkim.jijang.data.model.WorkType

class Converters {
    @TypeConverter
    fun fromFacilityPhase(value: FacilityPhase): String = value.name

    @TypeConverter
    fun toFacilityPhase(value: String): FacilityPhase = FacilityPhase.valueOf(value)

    @TypeConverter
    fun fromRouteType(value: RouteType): String = value.name

    @TypeConverter
    fun toRouteType(value: String): RouteType = RouteType.valueOf(value)

    @TypeConverter
    fun fromStockType(value: StockType): String = value.name

    @TypeConverter
    fun toStockType(value: String): StockType = StockType.valueOf(value)

    @TypeConverter
    fun fromLayingMethod(value: LayingMethod): String = value.name

    @TypeConverter
    fun toLayingMethod(value: String): LayingMethod = LayingMethod.valueOf(value)

    @TypeConverter
    fun fromMarkerType(value: MarkerType): String = value.name

    @TypeConverter
    fun toMarkerType(value: String): MarkerType = MarkerType.valueOf(value)

    @TypeConverter
    fun fromWorkType(value: WorkType): String = value.name

    @TypeConverter
    fun toWorkType(value: String): WorkType = WorkType.valueOf(value)

    @TypeConverter
    fun fromSpliceType(value: SpliceType?): String? = value?.name

    @TypeConverter
    fun toSpliceType(value: String?): SpliceType? = value?.let { SpliceType.valueOf(it) }

    @TypeConverter
    fun fromSafetyMeasure(value: SafetyMeasure?): String? = value?.name

    @TypeConverter
    fun toSafetyMeasure(value: String?): SafetyMeasure? = value?.let { SafetyMeasure.valueOf(it) }

    @TypeConverter
    fun fromMarkerStatus(value: MarkerStatus?): String? = value?.name

    @TypeConverter
    fun toMarkerStatus(value: String?): MarkerStatus? = value?.let { MarkerStatus.valueOf(it) }
}
