package com.thoughtworks.archguard.scanner2.domain.service

import org.archguard.model.Dependency
import org.archguard.model.code.JClass
import com.thoughtworks.archguard.scanner2.domain.model.toVO
import com.thoughtworks.archguard.scanner2.domain.repository.JClassRepository
import com.thoughtworks.archguard.scanner2.domain.repository.JMethodRepository
import org.archguard.operator.calculateFanInFanOutWithDependency
import org.archguard.model.FanInFanOut
import org.springframework.stereotype.Service

@Service
class FanInFanOutService(val jClassRepository: JClassRepository, val jMethodRepository: JMethodRepository) {
    suspend fun calculateAtClassLevel(systemId: Long): Map<String, FanInFanOut> {
        val allClassDependencies = jClassRepository.getAllClassDependenciesAndNotThirdParty(systemId).toSet()
        return calculateFanInFanOutWithDependency(allClassDependencies)
    }

    fun calculateAtMethodLevel(systemId: Long): Map<String, FanInFanOut> {
        val allMethodDependencies = jMethodRepository.getAllMethodDependenciesAndNotThirdParty(systemId).toSet()
        return calculateFanInFanOutWithDependency(allMethodDependencies)
    }

    fun calculateAtPackageLevel(systemId: Long, jClasses: List<JClass>): Map<String, FanInFanOut> {
        val allClassDependencies = jClassRepository.getAllClassDependenciesAndNotThirdParty(systemId)
        val packageDependencies = buildPackageDependencyFromClassDependency(allClassDependencies, jClasses).toSet()
        return calculateFanInFanOutWithDependency(packageDependencies)
    }

    fun calculateAtModuleLevel(systemId: Long, jClasses: List<JClass>): Map<String, FanInFanOut> {
        val allClassDependencies = jClassRepository.getAllClassDependenciesAndNotThirdParty(systemId)
        val moduleDependencies = buildModuleDependencyFromClassDependency(allClassDependencies, jClasses).toSet()
        return calculateFanInFanOutWithDependency(moduleDependencies)
    }

    fun buildModuleDependencyFromClassDependency(classDependencies: Collection<Dependency<String>>, jClasses: List<JClass>): List<Dependency<String>> {
        return classDependencies.map { classDependency ->
            val callerClass = toVO(jClasses.first { it.id == classDependency.caller })
            val calleeClass = toVO(jClasses.first { it.id == classDependency.callee })
            Dependency(callerClass.module!!, calleeClass.module!!)
        }
    }

    fun buildPackageDependencyFromClassDependency(classDependencies: Collection<Dependency<String>>, jClasses: List<JClass>): List<Dependency<String>> {
        return classDependencies.map { classDependency ->
            val callerClass = toVO(jClasses.first { it.id == classDependency.caller })
            val calleeClass = toVO(jClasses.first { it.id == classDependency.callee })
            Dependency(callerClass.module + "." + callerClass.getPackageName(), calleeClass.module + "." + calleeClass.getPackageName())
        }
    }
}

