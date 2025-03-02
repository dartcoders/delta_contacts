#import <Contacts/Contacts.h>
#import "ContactsChangeHistoryManager.h"

@implementation MyEventVisitor

- (instancetype)init {
    self = [super init];
    if (self) {
        _addedContacts = [NSMutableArray array];
        _updatedContacts = [NSMutableArray array];
        _shouldReset = NO;
    }
    return self;
}

- (void)visitDropEverythingEvent:(CNChangeHistoryDropEverythingEvent *)event {
    NSLog(@"Received a drop everything event.");
    self.shouldReset = YES;
}

- (void)visitAddContactEvent:(CNChangeHistoryAddContactEvent *)event {
    NSLog(@"The user added a contact with family name, %@, to their account with identifier %@.", event.contact.familyName, event.containerIdentifier);
    [self.addedContacts addObject:event.contact];
}

- (void)visitUpdateContactEvent:(CNChangeHistoryUpdateContactEvent *)event {
    NSLog(@"The user updated the contact with family name, %@.", event.contact.familyName);
    [self.updatedContacts addObject:event.contact];
}

@end

@implementation ContactsChangeHistoryManager

- (instancetype)init {
    self = [super init];
    if (self) {
        _contactStore = [[CNContactStore alloc] init];
    }
    return self;
}

- (NSDictionary *)fetchChangeHistoryWithKeys:(NSArray<id<CNKeyDescriptor>> *)keys {
    CNChangeHistoryFetchRequest *fetchRequest = [[CNChangeHistoryFetchRequest alloc] init];

    fetchRequest.startingToken = self.historyToken;

    fetchRequest.includeGroupChanges = YES;

    fetchRequest.additionalContactKeyDescriptors = keys;

    NSError *error = nil;
    CNFetchResult<NSEnumerator<CNChangeHistoryEvent *> *> *fetchResult = [self.contactStore enumeratorForChangeHistoryFetchRequest:fetchRequest error:&error];

    MyEventVisitor *myEventVisitor = [[MyEventVisitor alloc] init];
    
    if (!error && fetchResult.value) {
        for (CNChangeHistoryEvent *event in fetchResult.value) {
            [event acceptEventVisitor:myEventVisitor];
        }

        self.historyToken = fetchResult.currentHistoryToken;
        [self saveHistoryToken:self.historyToken];
    } else {
        NSLog(@"Error fetching change history: %@", error.localizedDescription);
    }
    
    return @{
        @"added": myEventVisitor.addedContacts,
        @"updated": myEventVisitor.updatedContacts,
        @"shouldReset": @(myEventVisitor.shouldReset)
    };
}

- (void)saveHistoryToken:(NSData *)token {
    [[NSUserDefaults standardUserDefaults] setObject:token forKey:@"LastHistoryToken"];
    [[NSUserDefaults standardUserDefaults] synchronize];
}

@end