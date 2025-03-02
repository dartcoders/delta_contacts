class ContactData {
  final String id;
  final String name;
  final List<String> phoneNumbers;
  final List<String> emails;

  ContactData({
    required this.id,
    required this.name,
    required this.phoneNumbers,
    required this.emails,
  });

  factory ContactData.fromJson(Map json) {
    return ContactData(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      phoneNumbers: ((json['phone_numbers'] ?? []) as List)
          .map((e) => e.toString())
          .toList(),
      emails:
          ((json['emails'] ?? []) as List).map((e) => e.toString()).toList(),
    );
  }
}
