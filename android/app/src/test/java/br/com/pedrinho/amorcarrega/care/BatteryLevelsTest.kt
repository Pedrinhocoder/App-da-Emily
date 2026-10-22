package br.com.pedrinho.amorcarrega.care

import br.com.pedrinho.amorcarrega.BatteryMonitorService
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryLevelsTest {
    @Test fun configuredReminderPreservesFixedStrongAndCriticalLevels() {
        assertEquals(Triple(30, 15, 5), BatteryMonitorService.computeLevels(30))
        assertEquals(Triple(42, 15, 5), BatteryMonitorService.computeLevels(42))
    }
    @Test fun reminderCannotOverrideStrongOrCriticalThresholds() {
        assertEquals(Triple(16, 15, 5), BatteryMonitorService.computeLevels(1))
        assertEquals(Triple(50, 15, 5), BatteryMonitorService.computeLevels(100))
    }
}
