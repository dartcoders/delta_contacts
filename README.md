# delta_contacts

- This plugin is used to get contacts from the device. Compatible with Android and iOS.

## Getting Started

```dart
import 'package:delta_contacts/delta_contacts.dart';

final deltaContacts = DeltaContacts();

final contacts = await deltaContacts.getContacts();

print(contacts);
```

Use history token to get the changes in contacts from the last time you synced. 
History token is timestamp in [millisecondsSinceEpoch] in Android and [Contact Store History Token](https://developer.apple.com/documentation/contacts/cncontactstore/currenthistorytoken) in iOS.

```dart
final contacts = await deltaContacts.getContacts(historyToken: historyToken);
```

## Pick Contact

Open the native contact picker and get the selected contact.
```dart
final contact = await deltaContacts.pickContact();

if (contact != null) {
  print(contact.name);
  print(contact.phoneNumbers);
  print(contact.emails);
}
```

Returns `ContactData?` — `null` if the user cancels the picker.

