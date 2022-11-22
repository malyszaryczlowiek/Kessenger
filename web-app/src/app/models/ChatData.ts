import { Chat } from "./Chat";
import { Message } from "./Message";
import { User } from "./User";

export interface ChatData {
    chat: Chat; 
    users: Array<User>;
    messages: Array<Message>;
}