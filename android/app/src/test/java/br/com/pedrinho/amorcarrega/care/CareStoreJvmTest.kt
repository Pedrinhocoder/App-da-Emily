package br.com.pedrinho.amorcarrega.care

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CareStoreJvmTest : CareStoreContract() {
    override val context get() = RuntimeEnvironment.getApplication()
}
