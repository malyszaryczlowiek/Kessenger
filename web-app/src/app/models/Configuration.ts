import { ChatPartitionsOffsets } from "./ChatPartitionsOffsets";
import { User } from "./User";

export interface Configuration {
    me: User;
    joiningOffset: number;
    chats: Array<ChatPartitionsOffsets>
}
