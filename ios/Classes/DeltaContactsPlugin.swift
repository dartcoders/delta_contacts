import Contacts
import ContactsUI
import Flutter
import Foundation
import UIKit

@objc public class DeltaContactsPlugin: NSObject, FlutterPlugin {
    private let contactStore = CNContactStore()
    
    @objc public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "delta_contacts", binaryMessenger: registrar.messenger())
        let instance = DeltaContactsPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    @objc public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "getContacts":
            let args = call.arguments as? [String: Any]
            let tokenString: String? =  args?["historyToken"] as? String
            let tokenData: Data? = (tokenString != nil && !tokenString!.isEmpty) ? Data(base64Encoded: tokenString!) : nil
            self.fetchContacts(tokenData: tokenData) { contactResult in
            switch contactResult {
            case .success(let contactData):
                let (added, updated, newToken) = contactData
                
                let addedDicts = added.map { self.contactToDictionary($0) }
                let updatedDicts = updated.map { self.contactToDictionary($0) }
                
                let resultDict: [String: Any] = [
                    "historyToken": newToken?.base64EncodedString() ?? "",
                    "contacts": addedDicts + updatedDicts,
                ]
                
                result(resultDict)
            case .failure(let error):
                result(FlutterError(code: "CONTACTS_ERROR", message: error.localizedDescription, details: nil))
            }
        }
        break
        default:
            result(FlutterMethodNotImplemented)
        }
    }

     private let keysToFetch: [CNKeyDescriptor] = [
        CNContactIdentifierKey,
        CNContactGivenNameKey,
        CNContactFamilyNameKey,
        CNContactPhoneNumbersKey,
        CNContactEmailAddressesKey
    ].map { $0 as CNKeyDescriptor }

    func fetchContacts(tokenData: Data?, completion: @escaping (Result<([CNContact], [CNContact], Data?), Error>) -> Void) {
        contactStore.requestAccess(for: .contacts) { [weak self] granted, error in
            guard let self = self else { return }
            if let error = error {
                DispatchQueue.main.async { completion(.failure(error)) }
                return
            }
            guard granted else {
                DispatchQueue.main.async { completion(.failure(ContactError.accessDenied)) }
                return
            }
            do {
                if tokenData == nil {
                    try self.performInitialFetch(completion: completion)
                } else {
                    if #available(iOS 13.0, *) {
                        try self.fetchContactChanges(tokenData: tokenData, completion: completion)
                    } else {
                        try self.performInitialFetch(completion: completion)
                    }
                }
            } catch {
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
    }


    private func contactToDictionary(_ contact: CNContact) -> [String: Any] {
        return [
            "id": contact.identifier,
            "name": contact.givenName + " " + contact.familyName,
            "phone_numbers": contact.phoneNumbers.map { $0.value.stringValue },
            "emails": contact.emailAddresses.map { $0.value as String }
        ]
    }

    private func performInitialFetch(completion: @escaping (Result<([CNContact], [CNContact], Data?), Error>) -> Void) throws {
        let request = CNContactFetchRequest(keysToFetch: keysToFetch)
        var contacts: [CNContact] = []
        try contactStore.enumerateContacts(with: request) { contact, _ in
            contacts.append(contact)
        }
        
        var newToken: Data? = nil
        if #available(iOS 13.0, *) {
            newToken = contactStore.currentHistoryToken
        }
        
        DispatchQueue.main.async { completion(.success((contacts, [], newToken))) }
    }

    @available(iOS 13.0, *)
    private func fetchContactChanges(tokenData: Data?, completion: @escaping (Result<([CNContact], [CNContact], Data?), Error>) -> Void) throws {
        guard let token = tokenData else {
            try performInitialFetch(completion: completion)
            return
        }
        
        let changeHistoryManager = ContactsChangeHistoryManager()
        changeHistoryManager.historyToken = tokenData
        
        guard let changes = changeHistoryManager.fetchChangeHistory(withKeys: keysToFetch) else {
            DispatchQueue.main.async {
                completion(.success(([], [], nil)))
            }
            return
        }
        
        var addedContacts: [CNContact] = []
        var updatedContacts: [CNContact] = []
        
        if let added = changes["added"] as? [CNContact] {
            addedContacts = added
        }
        
        if let updated = changes["updated"] as? [CNContact] {
            updatedContacts = updated
        }
        
        let shouldReset = (changes["shouldReset"] as? Bool) ?? false
        
        if shouldReset {
            try performInitialFetch(completion: completion)
        } else {
            DispatchQueue.main.async {
                completion(.success((addedContacts, updatedContacts, changeHistoryManager.historyToken)))
            }
        }
    }

    enum ContactError: Error {
        case accessDenied
    }
}