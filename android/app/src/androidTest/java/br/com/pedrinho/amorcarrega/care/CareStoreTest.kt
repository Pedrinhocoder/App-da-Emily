package br.com.pedrinho.amorcarrega.care

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareStoreTest : CareStoreContract() {
    override val context get() = InstrumentationRegistry.getInstrumentation().targetContext
}
