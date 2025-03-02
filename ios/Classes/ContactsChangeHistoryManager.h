#import <Foundation/Foundation.h>
#import <Contacts/Contacts.h>


@interface MyEventVisitor : NSObject <CNChangeHistoryEventVisitor>
@property (nonatomic, strong) NSMutableArray<CNContact *> *addedContacts;
@property (nonatomic, strong) NSMutableArray<CNContact *> *updatedContacts;
@property (nonatomic, assign) BOOL shouldReset;
@end

@interface ContactsChangeHistoryManager : NSObject

@property (nonatomic, strong) CNContactStore *contactStore;
@property (nonatomic, copy) NSData *historyToken;

- (NSDictionary *)fetchChangeHistoryWithKeys:(NSArray<id<CNKeyDescriptor>> *)keys;
- (void)saveHistoryToken:(NSData *)token;

@end