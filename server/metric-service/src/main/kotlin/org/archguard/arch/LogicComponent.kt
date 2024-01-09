package org.archguard.arch

import com.fasterxml.jackson.annotation.JsonIgnore

abstract class LogicComponent {
    open fun add(logicComponent: LogicComponent) {
        throw RuntimeException("Not Impl in abstract class or leaf node.")
    }

    open fun remove(logicComponent: LogicComponent) {
        throw RuntimeException("Not Impl in abstract class or leaf node.")
    }

    @JsonIgnore
    open fun getSubLogicComponent(): List<LogicComponent> {
        throw RuntimeException("Not Impl in abstract class or leaf node.")
    }

    abstract fun containsOrEquals(logicComponent: LogicComponent): Boolean
    abstract fun getFullName(): String
    abstract fun getType(): LogicModuleMemberType

    abstract override fun toString(): String
}
