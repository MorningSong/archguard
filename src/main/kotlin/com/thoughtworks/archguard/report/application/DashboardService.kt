package com.thoughtworks.archguard.report.application

import com.thoughtworks.archguard.report.controller.GroupData
import com.thoughtworks.archguard.report.domain.badsmell.BadSmellType
import com.thoughtworks.archguard.report.domain.circulardependency.CircularDependencyService
import com.thoughtworks.archguard.report.domain.dataclumps.DataClumpsService
import com.thoughtworks.archguard.report.domain.deepinheritance.DeepInheritanceService
import com.thoughtworks.archguard.report.domain.hub.HubService
import com.thoughtworks.archguard.report.domain.overview.calculator.BadSmellCalculator
import com.thoughtworks.archguard.report.domain.sizing.SizingService
import com.thoughtworks.archguard.report.infrastructure.influx.InfluxDBClient
import org.springframework.stereotype.Service

@Service
class DashboardService(val badSmellCalculator: BadSmellCalculator,
                       val sizingService: SizingService,
                       val hubService: HubService,
                       val dataClumpsService: DataClumpsService,
                       val deepInheritanceService: DeepInheritanceService,
                       val circularDependencyService: CircularDependencyService,
                       val influxDBClient: InfluxDBClient) {

    fun getDashboard(systemId: Long): List<Dashboard> {
        val couplingDashboard = getCouplingDashboard(systemId)
        val sizingDashboard = getSizingDashboard(systemId)
        return listOf(couplingDashboard, sizingDashboard)
    }

    private fun getSizingDashboard(systemId: Long): Dashboard {
        return Dashboard(DashboardGroup.SIZING,
                listOf(
                        GroupData(BadSmellType.SIZINGMODULES, badSmellCalculator.calculateBadSmell(BadSmellType.SIZINGMODULES, systemId).level,
                                listOf(GraphData("2020-10-1", 4),
                                        GraphData("2020-10-5", 3),
                                        GraphData("2020-10-12", 9),
                                        GraphData("2020-10-15", 8),
                                        GraphData("2020-10-16", 20))),
                        GroupData(BadSmellType.SIZINGPACKAGE, badSmellCalculator.calculateBadSmell(BadSmellType.SIZINGPACKAGE, systemId).level,
                                listOf(GraphData("2020-10-1", 3),
                                        GraphData("2020-10-5", 4),
                                        GraphData("2020-10-12", 15),
                                        GraphData("2020-10-15", 20),
                                        GraphData("2020-10-16", 9))),
                        GroupData(BadSmellType.SIZINGCLASS, badSmellCalculator.calculateBadSmell(BadSmellType.SIZINGCLASS, systemId).level,
                                listOf(GraphData("2020-10-1", 303),
                                        GraphData("2020-10-5", 34),
                                        GraphData("2020-10-12", 15),
                                        GraphData("2020-10-15", 95),
                                        GraphData("2020-10-16", 39))),
                        GroupData(BadSmellType.SIZINGMETHOD, badSmellCalculator.calculateBadSmell(BadSmellType.SIZINGMETHOD, systemId).level,
                                listOf(GraphData("2020-10-1", 3),
                                        GraphData("2020-10-5", 334),
                                        GraphData("2020-10-12", 235),
                                        GraphData("2020-10-15", 35),
                                        GraphData("2020-10-16", 129)))
                ))
    }

    private fun getCouplingDashboard(systemId: Long): Dashboard {
        return Dashboard(DashboardGroup.COUPLING,
                listOf(
                        GroupData(BadSmellType.DATACLUMPS, badSmellCalculator.calculateBadSmell(BadSmellType.DATACLUMPS, systemId).level,
                                listOf(GraphData("2020-10-1", 4),
                                        GraphData("2020-10-5", 10),
                                        GraphData("2020-10-12", 5),
                                        GraphData("2020-10-15", 8),
                                        GraphData("2020-10-16", 20))),
                        GroupData(BadSmellType.DEEPINHERITANCE, badSmellCalculator.calculateBadSmell(BadSmellType.DEEPINHERITANCE, systemId).level,
                                listOf(GraphData("2020-10-1", 3),
                                        GraphData("2020-10-5", 4),
                                        GraphData("2020-10-12", 5),
                                        GraphData("2020-10-15", 5),
                                        GraphData("2020-10-16", 9)))
                ))
    }

    fun saveReport(systemId: Long) {
        val sizingReport = sizingService.getSizingReport(systemId).map { "${it.key}=${it.value}" }.joinToString(",")
        influxDBClient.save("sizing_report,system_id=${systemId} ${sizingReport}")

        val hubReport = hubService.getHubReport(systemId).map { "${it.key}=${it.value}" }.joinToString(",")
        val dataClumpsReport = dataClumpsService.getDataClumpReport(systemId).map { "${it.key}=${it.value}" }.joinToString(",")
        val deepInheritanceReport = deepInheritanceService.getDeepInheritanceReport(systemId).map { "${it.key}=${it.value}" }.joinToString(",")
        val circularDependencyReport = circularDependencyService.getCircularDependencyReport(systemId).map { "${it.key}=${it.value}" }.joinToString(",")
        influxDBClient.save("coupling_report,system_id=${systemId} " +
                "${hubReport},${dataClumpsReport},${deepInheritanceReport},${circularDependencyReport}")
    }


}

class Dashboard(eDashboardGroup: DashboardGroup, val groupData: List<GroupData>) {
    var dashboardGroup: String = eDashboardGroup.value
}

enum class DashboardGroup(var value: String) {
    COUPLING("耦合度"),
    SIZING("体量"),
    COHESION("内聚度"),
    REDUNDANCY("冗余度")
}


data class GraphData(val date: String, val value: Int)

