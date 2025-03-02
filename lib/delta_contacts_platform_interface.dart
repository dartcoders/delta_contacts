import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'delta_contacts_method_channel.dart';

abstract class DeltaContactsPlatform extends PlatformInterface {
  /// Constructs a DeltaContactsPlatform.
  DeltaContactsPlatform() : super(token: _token);

  static final Object _token = Object();

  static DeltaContactsPlatform _instance = MethodChannelDeltaContacts();

  /// The default instance of [DeltaContactsPlatform] to use.
  ///
  /// Defaults to [MethodChannelDeltaContacts].
  static DeltaContactsPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [DeltaContactsPlatform] when
  /// they register themselves.
  static set instance(DeltaContactsPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future getAndroidContacts({num? lastUpdatedAt});

  Future getIosContacts({String? historyToken});
}
