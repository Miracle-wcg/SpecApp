package com.wcg.app.specapp.business

import java.text.SimpleDateFormat
import java.util.*

object FileNameGenerator {
    /**
     * 根据模板和参数，生成实时的文件名称
     */
    fun generate(template: String, operatorName: String, batchNumber: String, extension: String): String {
        val now = Date()
        val sdfFull = SimpleDateFormat("yyyyMMdd_HHmmss")
        val sdfDate = SimpleDateFormat("yyyyMMdd")
        val sdfTime = SimpleDateFormat("HHmmss")

        var result = template
        result = result.replace("[操作员]", operatorName).replace("[Operator]", operatorName)
        result = result.replace("[批次号]", batchNumber).replace("[Batch]", batchNumber)
        result = result.replace("[时间戳]", sdfFull.format(now)).replace("[Timestamp]", sdfFull.format(now))
        result = result.replace("[日期]", sdfDate.format(now)).replace("[Date]", sdfDate.format(now))
        result = result.replace("[时间]", sdfTime.format(now)).replace("[Time]", sdfTime.format(now))

        return "$result.${extension.lowercase()}"
    }
}