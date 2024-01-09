package org.archguard.architecture.view.concept

/**
 * A module in Maven or Gradle should focus on single responsibility in concept level.
 * So it should be a CComponent or a CConnector.
 * If it has more than one identity，may need be divided to more than one module.
 */
class ConceptArchitecture(
    val type: org.archguard.architecture.view.concept.ConceptType,
    val domainModels: List<org.archguard.architecture.view.concept.DomainModel>,
    val reliability: Float,
    val desc: String,
    val comment: String
)

