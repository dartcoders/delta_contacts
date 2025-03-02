package com.dartcoders.delta_contacts

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlin.test.Test
import org.mockito.Mockito

internal class DeltaContactsPluginTest {
  @Test
  fun onMethodCall_getPlatformVersion_returnsExpectedValue() {
    val plugin = DeltaContactsPlugin()

    val call = MethodCall("getPlatformVersion", null)
    val mockResult: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)
    plugin.onMethodCall(call, mockResult)

    Mockito.verify(mockResult).success("Android " + android.os.Build.VERSION.RELEASE)
  }
}
