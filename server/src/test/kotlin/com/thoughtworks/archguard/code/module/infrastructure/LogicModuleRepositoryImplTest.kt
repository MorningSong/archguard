package com.thoughtworks.archguard.code.module.infrastructure

import com.thoughtworks.archguard.code.module.domain.LogicModuleRepository
import com.thoughtworks.archguard.code.module.domain.model.JClassVO
import com.thoughtworks.archguard.code.module.domain.model.LeafManger
import org.archguard.arch.LogicModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
internal class LogicModuleRepositoryImplTest {

    @Autowired
    lateinit var logicModuleRepository: LogicModuleRepository

    @Test
    @Sql("classpath:sqls/insert_logic_module.sql")
    internal fun `should only select normal data from logic module`() {
        val systemId: Long = 1
        val normalLogicModules = logicModuleRepository.getAllByShowStatus(systemId, true)
        assertThat(normalLogicModules.size).isEqualTo(1)
        assertThat(normalLogicModules[0]).usingRecursiveComparison().isEqualTo(
            LogicModule("id1", "dubbo-provider", listOf(LeafManger.createLeaf("dubbo-provider")))
        )
    }

    @Test
    @Sql("classpath:sqls/insert_logic_module.sql")
    internal fun `should select all data from logic module`() {
        val systemId: Long = 1
        val logicModules = logicModuleRepository.getAllBySystemId(systemId)
        assertThat(logicModules.size).isEqualTo(4)
        val lg1 = LogicModule("id1", "dubbo-provider", listOf(LeafManger.createLeaf("dubbo-provider")))
        val lg2 = LogicModule("id2", "dubbo-consumer", listOf(LeafManger.createLeaf("dubbo-consumer")))
        lg2.hide()
        val lg3 = LogicModule.create("id3", "dubbo-all", emptyList(), listOf(lg2, lg1))
        lg3.hide()
        val lg4 = LogicModule.create("id4", "dubbo-top", listOf(JClassVO.create("dubbo-api.DemoService")), listOf(lg3))
        lg4.hide()
        assertThat(logicModules).usingRecursiveFieldByFieldElementComparator().containsAll(
            listOf(
                lg1, lg2, lg3, lg4
            )
        )
    }
}
