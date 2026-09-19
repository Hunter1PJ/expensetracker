package com.suguru.expensetracker.domain.usecase.export

import com.suguru.expensetracker.data.export.CsvExporter
import com.suguru.expensetracker.domain.model.export.ExportFilter
import java.io.OutputStream
import java.time.ZoneId

/**
 * Generates a filtered CSV export based on user-selected criteria.
 * Gated behind Pro entitlement.
 */
class GenerateAdvancedCsvExportUseCase(
    private val prepareFinancialReportDataUseCase: PrepareFinancialReportDataUseCase,
    private val csvExporter: CsvExporter
) {

    suspend fun exportToStream(
        filter: ExportFilter,
        outputStream: OutputStream,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val reportData = prepareFinancialReportDataUseCase(filter, zoneId)
        csvExporter.exportToStream(reportData.transactions, outputStream, zoneId)
    }

    suspend fun exportToString(
        filter: ExportFilter,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val reportData = prepareFinancialReportDataUseCase(filter, zoneId)
        return csvExporter.exportToString(reportData.transactions, zoneId)
    }
}
