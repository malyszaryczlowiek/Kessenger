import { EventEmitter } from "@angular/core";
import { Chat } from "./Chat";
import { Message } from "./Message";
import { MessagePartOff } from "./MesssagePartOff";
import { User } from "./User";

export interface ChatData {
    chat: Chat; 
    partitionOffsets: Array<{partition: number, offset: number}>;
    users: Array<User>;
    messages: Array<Message>;
    unreadMessages: Array<MessagePartOff>;
    emitter: EventEmitter<ChatData>;
}