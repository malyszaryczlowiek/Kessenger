import { EventEmitter } from "@angular/core";
import { Chat } from "./Chat";
import { Message } from "./Message";
import { MessagePartOff } from "./MesssagePartOff";
import { PartitionOffset } from "./PartitionOffset";
import { User } from "./User";

export interface ChatData {
    chat: Chat; 
    partitionOffsets: PartitionOffset[];
    users: Array<User>;
    messages: Array<Message>;
    unreadMessages: Array<MessagePartOff>;
    isNew?: boolean;
    emitter: EventEmitter<ChatData>;
}