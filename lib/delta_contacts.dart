import 'dart:io';
import 'package:delta_contacts/model/delta_contacts_response.dart';
import 'delta_contacts_platform_interface.dart';

class DeltaContacts {
  Future<DeltaContactsResponse> getContacts({String? historyToken}) async {
    if (Platform.isAndroid) {
      num? lastUpdatedAt;
      if (historyToken != null) lastUpdatedAt = num.parse(historyToken);
      var data = await DeltaContactsPlatform.instance.getAndroidContacts(lastUpdatedAt: lastUpdatedAt);
      return DeltaContactsResponse.fromAndroid(data);
    } else {
      var data = await DeltaContactsPlatform.instance.getIosContacts(historyToken: historyToken);
      return DeltaContactsResponse.fromIos(data);
    }
  }
}
