import 'package:flutter/material.dart';
import 'package:delta_contacts/delta_contacts.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  Future<void> _getContacts() async {
    final DeltaContacts deltaContactsPlugin = DeltaContacts();
    String? historyToken;

    /// Do not pass the [historyToken] if you want to get all contacts
    final response =
        await deltaContactsPlugin.getContacts(historyToken: historyToken);
    historyToken = response.historyToken ?? '';
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Delta Contacts'),
        ),
        body: Center(
          child: ElevatedButton(
            onPressed: _getContacts,
            child: const Text('Get Delta Contacts'),
          ),
        ),
      ),
    );
  }
}
