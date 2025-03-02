import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'delta_contacts_platform_interface.dart';

/// An implementation of [DeltaContactsPlatform] that uses method channels.
class MethodChannelDeltaContacts extends DeltaContactsPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('delta_contacts');

  @override
  Future getAndroidContacts({num? lastUpdatedAt}) async {
    final contacts = await methodChannel.invokeMethod('getContacts', {
      if (lastUpdatedAt != null) 'lastUpdatedAt': lastUpdatedAt,
    });
    return contacts ?? [];
  }

  @override
  Future getIosContacts({String? historyToken}) async {
    final contacts = await methodChannel.invokeMethod('getContacts', {
      if (historyToken != null) 'historyToken': historyToken,
    });
    return contacts ?? {};
  }
}
