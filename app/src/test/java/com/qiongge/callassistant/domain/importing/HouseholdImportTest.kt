package com.qiongge.callassistant.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Test

class HouseholdImportTest {
    @Test
    fun recognizesExplicitColumnsAndPreservesLeadingZeroes() {
        val header = listOf("楼栋号", "门牌号", "姓名", "手机号1", "备用电话")
        val cells = listOf("05", "0802", "甲", "１３８０００００００１、13900000002", "13600000003")
        val csv = listOf(header, cells).joinToString("\n") { it.joinToString(",") }.encodeToByteArray()
        val table = TabularFileReader.read("owners.csv", csv)
        assertEquals(PropertyFields("05", "0802"), PropertyFields.recognize(table.rows.single(), table.headers))
        assertEquals(
            listOf("13800000001", "13900000002", "13600000003"),
            table.phoneColumns.flatMap { PhoneRecognition.candidates(cells[it]) },
        )
    }

    @Test
    fun recognizesBuildingAndUnitFromUnheadedOcrText() {
        assertEquals(
            PropertyFields("3栋", "2单元0802"),
            PropertyFields.recognize(listOf("某小区３栋 ２单元 ０８０２室", "13800000001")),
        )
        assertEquals(PropertyFields("5", "02-1201"), PropertyFields.recognize(listOf("5-02-1201")))
        assertEquals(PropertyFields(null, null), PropertyFields.recognize(listOf("13800000001", "预算200万")))
    }
}
