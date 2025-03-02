import 'package:delta_contacts/model/contact_data.dart';

class DeltaContactsResponse {
  final List<ContactData> contacts;
  final String? historyToken;

  DeltaContactsResponse({
    required this.contacts,
    this.historyToken,
  });

  factory DeltaContactsResponse.fromAndroid(List? contacts) {
    return DeltaContactsResponse(
      contacts: contacts?.map((contact) {
            return ContactData.fromJson(contact);
          }).toList() ??
          [],
      historyToken: DateTime.now().millisecondsSinceEpoch.toString(),
    );
  }

  factory DeltaContactsResponse.fromIos(Map json) {
    return DeltaContactsResponse(
      contacts: ((json['contacts'] ?? []) as List).map((contact) => ContactData.fromJson(contact)).toList(),
      historyToken: json['historyToken'] ?? '',
    );
  }
}
