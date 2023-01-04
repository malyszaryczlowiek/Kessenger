import { User } from "./User";

export interface Configuration {
    me: User;
    joiningOffset: number;
    chats: Array<{
        chatId: string;
        partitionOffset: Array<{
            partition: number;
            offset: number;
        }>
    }>
}
