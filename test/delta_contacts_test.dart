import 'package:flutter_test/flutter_test.dart';
import 'package:delta_contacts/delta_contacts.dart';
import 'package:delta_contacts/delta_contacts_platform_interface.dart';
import 'package:delta_contacts/delta_contacts_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockDeltaContactsPlatform
    with MockPlatformInterfaceMixin
    implements DeltaContactsPlatform {
  @override
  Future getAndroidContacts({num? lastUpdatedAt}) {
    return Future.value([]);
  }

  @override
  Future getIosContacts({String? historyToken}) {
    return Future.value([]);
  }

  @override
  Future pickContact() {
    return Future.value();
  }
}

void main() {
  final DeltaContactsPlatform initialPlatform = DeltaContactsPlatform.instance;

  test('$MethodChannelDeltaContacts is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelDeltaContacts>());
  });

  test('getContacts', () async {
    DeltaContacts deltaContactsPlugin = DeltaContacts();
    MockDeltaContactsPlatform fakePlatform = MockDeltaContactsPlatform();
    DeltaContactsPlatform.instance = fakePlatform;

    expect(await deltaContactsPlugin.getContacts(), []);
  });
}
